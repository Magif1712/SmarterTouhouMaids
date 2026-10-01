package com.github.magif1712.smarter_touhou_maids.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分支身份一致性：分支实现工厂所在包的末段，必须等于它的分支 id（或是在本类登记过的"有意领域名"）。
 * <p>
 * <b>为什么需要这条断言</b>（真善美第2条 · 模式一致）：装配图用 id 说话，目录用包名说话。
 * 若两者不对应（历史上曾如此：id {@code possession_sensor} 的实现住在
 * {@code possession_sensor_original} 包里——审计 ❹，已在 W9b 互换包名修好），
 * 读者必须在脑内维护一张映射表——认知成本上升，且极易在重构中改错目标。
 * <p>
 * <b>为什么用"包末段"而不是整个包路径</b>：装配图的每层都有独立目录（W7/W8 已把层级对齐），
 * 分支 id 只与"该层目录之下的实现包名"对应，与更上层路径无关。
 * <p>
 * <b>两类合法例外</b>（都必须登记，不允许默默不一致）：
 * <ul>
 *   <li>{@link #INTENTIONAL_DOMAIN_PACKAGE_NAMES}：包名比分支 id <b>更能描述"这是什么"</b>——
 *       如 {@code reflex_arc_system_agent}（反射弧系统机制）优于 id {@code smarter}。
 *       这是"真"（命名反映实在）优先于"与符号逐字一致"的取舍。</li>
 * </ul>
 * （审计 ❹ 的交叉错位已在 W9b 修好，故本类不再需要"待修清单"。）
 * <b>不参与本断言</b>：插槽的"产物工厂"<b>接口</b>（{@code AgentFactory}/{@code AiFactory}/
 * {@code ProcessFactory}/{@code NnFactory}/… 它们代表插槽契约，不是某个分支的实现）。
 * <p>
 * <b>字形</b>（设计原则5）：辅助方法 {@code void} + DPS 出参。
 */
class BranchIdentityTest {

    /** 生产源码根（相对项目根）。 */
    private static final Path MAIN_JAVA =
            Paths.get("src/main/java/com/github/magif1712/smarter_touhou_maids");
    /** 包声明。 */
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");

    /**
     * 有意的领域名：包名描述"这是什么"，比分支 id 更有信息量，故保留。
     * <p>登记即等于"我们有意让二者不同"，任何新增都必须在此说明理由。
     */
    private static final List<String> INTENTIONAL_DOMAIN_PACKAGE_NAMES = List.of(
            "reflex_arc_system_agent",   // 分支 id = smarter（包名描述反射弧系统机制）
            "original_cnn",              // 分支 id = cnn（与 standard_bnn / cnn_active 对称，表达"原初版本"）
            "original_bnn");             // 分支 id = bnn（同上）

    /** 待修错位清单：审计 ❹ 已修（W9b 互换两包名），故此处为空。 */
    private static final List<String> PENDING_IDENTITY_MISMATCH = List.of();

    @Test
    void 分支实现工厂的包名必须等于其分支id或登记为领域名() {
        Set<String> branchIds = new TreeSet<>();
        ArchitectureFacts.BRANCHES_BY_NODE.values().forEach(branchIds::addAll);

        List<Path> files = new ArrayList<>();
        collectFactoryFiles(MAIN_JAVA /*->*/, files);

        List<String> offenders = new ArrayList<>();
        for (Path file : files) {
            String content = readContent(file);
            if (!isBranchImplementationFactory(content)) {
                continue;   // 插槽产物接口 / 纯工具类（如 SaveSlotFactory）：不参与
            }
            String segment = packageSegment(content);
            if (segment == null) {
                offenders.add(MAIN_JAVA.relativize(file) + " :: 无 package 声明");
                continue;
            }
            if (branchIds.contains(segment)
                    || INTENTIONAL_DOMAIN_PACKAGE_NAMES.contains(segment)
                    || PENDING_IDENTITY_MISMATCH.contains(segment)) {
                continue;
            }
            offenders.add(MAIN_JAVA.relativize(file).toString().replace('\\', '/')
                    + " :: 包末段 = " + segment + "（既不是分支 id，也未登记为领域名）");
        }
        assertTrue(offenders.isEmpty(),
                "分支实现工厂的包名与分支 id 不一致——要么改名对齐（首选），"
                        + "要么在 INTENTIONAL_DOMAIN_PACKAGE_NAMES 登记理由：\n" + String.join("\n", offenders));
    }

    @Test
    void 待修的交叉错位在修复前必须仍然存在() {
        Set<String> packageSegments = new TreeSet<>();
        collectPackageSegments(MAIN_JAVA /*->*/, packageSegments);
        for (String pending : PENDING_IDENTITY_MISMATCH) {
            assertTrue(packageSegments.contains(pending),
                    "已登记的错位包已不存在：" + pending
                            + " —— 若已修复，请从 PENDING_IDENTITY_MISMATCH 移除（清单必须与实物同步）");
        }
    }

    // ==================== 扫描（DPS：入参 + /*->*/ 出参） ====================

    /** 收集全部 {@code *Factory.java}。 */
    private static void collectFactoryFiles(Path root /*->*/, List<Path> out) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("Factory.java"))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 分支实现工厂：具体类（{@code public class}）+ 实现某接口（{@code implements}）。 */
    private static boolean isBranchImplementationFactory(String content) {
        return content.contains("public class ") && content.contains("implements ");
    }

    /** 包声明的最后一段（如 {@code …smarter.mapper.bnn_mapper} → {@code bnn_mapper}）。 */
    private static String packageSegment(String content) {
        for (String line : content.split("\\R")) {
            Matcher matcher = PACKAGE.matcher(line);
            if (matcher.find()) {
                String pkg = matcher.group(1);
                return pkg.substring(pkg.lastIndexOf('.') + 1);
            }
        }
        return null;
    }

    /** 收集全部 java 文件的包末段。 */
    private static void collectPackageSegments(Path root /*->*/, Set<String> out) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path file : files) {
            String segment = packageSegment(readContent(file));
            if (segment != null) {
                out.add(segment);
            }
        }
    }

    private static String readContent(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
