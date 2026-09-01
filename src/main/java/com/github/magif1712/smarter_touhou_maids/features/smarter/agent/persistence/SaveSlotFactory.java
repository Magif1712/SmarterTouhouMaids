package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 持久化槽位工厂：创建/查找 {@link SaveSlot}，管理版本目录，清理旧版本。
 * <p>
 * 把"maid + 根叶路径 + 版本"三维定位实在化为 {@link SaveSlot} 对象（真善美第4条），
 * 并管理版本生命周期（C1 per-maid per-path per-version、C6 版本保留策略）。
 * <p>
 * <b>目录结构</b>（C1）：
 * <pre>{@code
 * <baseDir>/                            ← baseDir 由 WorldPersistenceDir 算（单人=WorldSaveDir，多人=GAMEDIR/servers/<ip>）
 *   └─ <maidUUID>/
 *      └─ <path-token>/                  如 smarter__process_ai__urana__standard_bnn
 *         └─ v<timestamp>/                版本目录（timestamp = 创建时的 System.currentTimeMillis()）
 *            ├─ nn/                       layerId="nn"
 *            ├─ urana/                    layerId="urana"
 *            └─ ...
 * }</pre>
 * <p>
 * <b>本工厂不感知世界</b>（真善美第3条）：pathDir 由上层（{@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterClientService}）
 * 在 init 时算好传入——单人/多人/Realm 模式切换时本工厂零改动。世界感知逻辑集中在
 * {@link WorldPersistenceDir}（唯一感知 {@code Minecraft.getInstance()} 的类）。
 * <p>
 * <b>load vs save 版本策略</b>（C6）：
 * <ul>
 *   <li><b>load</b>（{@link #latestOrNew}）：取最新已有版本目录。无版本时返回指向新版本路径的 slot
 *       （路径未创建，各层 load 见到文件缺失则保持默认——优雅降级）。</li>
 *   <li><b>save</b>（{@link #newVersion}）：始终创建新时间戳版本目录，写新版本。每次 save 产新版本，
 *       不覆盖旧版本——与"版本保留策略"一致。</li>
 * </ul>
 * <p>
 * <b>版本清理</b>（{@link #pruneOldVersions}）：按 timestamp 降序排列版本目录，超 maxRetention 时删最老。
 */
public final class SaveSlotFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger("SaveSlotFactory");

    /** 版本目录前缀（v + timestamp）。 */
    private static final String VERSION_PREFIX = "v";

    private SaveSlotFactory() {
    }

    /**
     * 取最新已有版本槽位（load 用）。无版本时返回指向新版本路径的 slot（未创建，优雅降级）。
     *
     * @param pathDir 版本目录的父目录（baseDir/maidUUID/pathToken，由上层算好传入）
     * @return 最新版本 slot；无版本时返回新版本路径 slot（未创建）
     */
    public static SaveSlot latestOrNew(Path pathDir) {
        Path latest = findLatestVersion(pathDir);
        if (latest != null) {
            return new SaveSlotImpl(latest.toAbsolutePath().toString());
        }
        // 无已有版本：返回指向新版本路径的 slot（不创建目录；各层 load 见文件缺失则保持默认）
        return new SaveSlotImpl(newVersionPath(pathDir).toAbsolutePath().toString());
    }

    /**
     * 创建新时间戳版本槽位（save 用）。每次 save 产新版本，不覆盖旧版本。
     *
     * @param pathDir 版本目录的父目录（baseDir/maidUUID/pathToken，由上层算好传入）
     * @return 新版本 slot（目录已创建）
     */
    public static SaveSlot newVersion(Path pathDir) {
        Path versionDir = newVersionPath(pathDir);
        try {
            Files.createDirectories(versionDir);
        } catch (IOException e) {
            LOGGER.error("[SaveSlotFactory] 创建版本目录失败: {}", versionDir, e);
        }
        return new SaveSlotImpl(versionDir.toAbsolutePath().toString());
    }

    /**
     * 清理旧版本：按 timestamp 降序排列，超 maxRetention 时删最老。
     * <p>
     * maxRetention ≤ 0 时不清理（保留全部）。
     *
     * @param pathDir 版本目录的父目录（baseDir/maidUUID/pathToken，由上层算好传入）
     * @param maxRetention 最大保留版本数
     */
    public static void pruneOldVersions(Path pathDir, int maxRetention) {
        if (maxRetention <= 0) return;
        if (!Files.isDirectory(pathDir)) return;

        File[] versions = listVersionDirs(pathDir);
        if (versions.length <= maxRetention) return;

        // 按 timestamp 降序（最新在前），删除超出 maxRetention 的最老版本
        Arrays.sort(versions, Comparator.comparingLong(SaveSlotFactory::extractTimestamp).reversed());
        for (int i = maxRetention; i < versions.length; i++) {
            try {
                deleteRecursively(versions[i].toPath());
            } catch (IOException e) {
                LOGGER.warn("[SaveSlotFactory] 删除旧版本失败: {}", versions[i], e);
            }
        }
    }

    // ==================== 内部 ====================

    private static Path newVersionPath(Path pathDir) {
        return pathDir.resolve(VERSION_PREFIX + System.currentTimeMillis());
    }

    /**
     * 找最新版本目录（timestamp 最大的 v<ts>）。无版本返回 null。
     */
    private static Path findLatestVersion(Path pathDir) {
        if (!Files.isDirectory(pathDir)) return null;
        File[] versions = listVersionDirs(pathDir);
        if (versions.length == 0) return null;
        File latest = Arrays.stream(versions)
                .max(Comparator.comparingLong(SaveSlotFactory::extractTimestamp))
                .orElse(null);
        return latest != null ? latest.toPath() : null;
    }

    /**
     * 列出 pathDir 下所有 v<timestamp> 目录（非递归）。
     */
    private static File[] listVersionDirs(Path pathDir) {
        File[] files = pathDir.toFile().listFiles((dir, name) ->
                name.startsWith(VERSION_PREFIX) && new File(dir, name).isDirectory());
        return files != null ? files : new File[0];
    }

    /**
     * 从版本目录名提取 timestamp（v<ts> → ts）。解析失败返回 0（排最前=最老）。
     */
    private static long extractTimestamp(File versionDir) {
        try {
            return Long.parseLong(versionDir.getName().substring(VERSION_PREFIX.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            LOGGER.warn("[SaveSlotFactory] 删除文件失败: {}", p, e);
                        }
                    });
        }
    }

    /**
     * SaveSlot 实现：只持 rootPath，layerPath 派生（final 不可变）。
     */
    private record SaveSlotImpl(String rootPath) implements SaveSlot {
        @Override
        public String rootPath() {
            return rootPath;
        }

        @Override
        public String layerPath(String layerId) {
            return new File(rootPath, layerId).getAbsolutePath();
        }
    }
}
