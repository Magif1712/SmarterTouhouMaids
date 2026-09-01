package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * 世界感知持久化根目录计算器：唯一感知世界/服务器上下文的地方（真善美第4条：把"世界身份"
 * 这个不实在的概念，实在化为 {@link Path baseDir}）。
 * <p>
 * <b>单人/LAN 主机</b>：{@code <WorldSaveDir>/smarter_touhou_maids/persistence/}——数据随存档走，
 * 复制存档即复制数据，删存档即删数据（自然隔离）。
 * <p>
 * <b>多人</b>：{@code <GAMEDIR>/smarter_touhou_maids/persistence/servers/<sanitized-ip>/}——
 * 不同服务器隔离，重连同服数据保留。客户端无本地世界存档，服务器 IP 是最佳稳定标识。
 * <p>
 * <b>回退</b>（主菜单/异常）：{@code <GAMEDIR>/smarter_touhou_maids/persistence/local/}——
 * 不应发生于 smarterReady=true 时（此时必在游戏中），仅防御。
 * <p>
 * <b>调用时机</b>：仅在客户端线程（如 {@code onClientTick}）调用，此时 {@link Minecraft}
 * 已就绪、世界已加载，{@code getSingleplayerServer()} / {@code getCurrentServer()} 二者必有其一非空。
 * <p>
 * <b>真善美第3条</b>：上层（{@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterClientService}）
 * 只调 {@link #computeBaseDir()}，不感知单人/多人/Realm 模式切换——换模式时上层零改动。
 */
@OnlyIn(Dist.CLIENT)
public final class WorldPersistenceDir {

    /**
     * 服务器 key 最大长度。远低于文件系统 255 字节上限，保持目录列表可读。
     * <p>
     * 意识域 C 中"服务器 IP/名称 → 安全目录名"变换有"限制长度"模式，但没有"64 是魔法数字"模式——
     * 故提取为命名常量 + 注释说明依据，如实体现"长度限制"模式而不引入来历不明的硬编码值。
     */
    private static final int MAX_SERVER_KEY_LENGTH = 64;

    private WorldPersistenceDir() {
    }

    /**
     * 计算 world-aware 持久化根目录。
     *
     * @return 持久化根目录路径（单人=世界存档目录，多人=GAMEDIR/servers/&lt;key&gt;，回退=GAMEDIR/local）
     */
    public static Path computeBaseDir() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer sps = mc.getSingleplayerServer();
        Path base;
        if (sps != null) {
            // 单人/LAN 主机：随存档走
            base = sps.getWorldPath(new LevelResource(""))
                    .resolve("smarter_touhou_maids").resolve("persistence");
        } else {
            ServerData sd = mc.getCurrentServer();
            if (sd != null) {
                // 多人：按服务器 ip 键控
                String key = sanitizeServerKey(sd.ip, sd.name);
                base = FMLPaths.GAMEDIR.get()
                        .resolve("smarter_touhou_maids").resolve("persistence")
                        .resolve("servers").resolve(key);
            } else {
                // 回退（不应发生于 gameplay，仅防御）
                base = FMLPaths.GAMEDIR.get()
                        .resolve("smarter_touhou_maids").resolve("persistence")
                        .resolve("local");
            }
        }
        // 统一收尾：toAbsolutePath() 把相对路径（单人 getWorldPath(new LevelResource(""))
        // 返回 .\saves\<world>）转绝对；normalize() 折叠 . 与 .. 冗余分量，
        // 消除 run\.\saves 中的 . —— 修复 .\ 混入路径的 bug。
        // 真善美第2条：三个分支模式统一收尾，不分单人/多人/回退。
        return base.toAbsolutePath().normalize();
    }

    /**
     * 服务器 IP/名称 → 安全目录名：仅保留 [A-Za-z0-9._-]，折叠连续下划线，限长。
     * <p>
     * ip 优先（比 name 更稳定），ip 为空时回退 name，均为空回退 "unknown"。
     */
    private static String sanitizeServerKey(String ip, String name) {
        String raw = (ip == null || ip.isBlank()) ? name : ip;
        if (raw == null || raw.isBlank()) return "unknown";
        String s = raw.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        s = s.replaceAll("_+", "_");
        if (s.length() > MAX_SERVER_KEY_LENGTH) {
            s = s.substring(0, MAX_SERVER_KEY_LENGTH);
        }
        return s.isBlank() ? "unknown" : s;
    }
}
