package com.github.magif1712.smarter_touhou_maids.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 骨架面（{@code features/smarter/infrastructure/**}）的边界校验。
 * <p>
 * <b>为什么需要本类</b>（公理(1) + 设计原则4）：骨架（装配机制 / 驱动 / 存取 / 展示 / 执行 / 网络 /
 * 附身 / 状态 —— "让模式存在并运转"的一切支撑物）本身是**人设计的系统**，故它必然是一个映射系统，
 * 必须有自己实在的包。它只与模式的**内涵**（各概念的契约与共同机制）对话，**不与任何候选系统的实现耦合**
 * ——否则"换一个实现动一处"就不成立（真善美第2条）。这条纪律若只写在文档里就是不实在的，故实在化为断言。
 * <p>
 * <b>W48 记录（面名与面界）</b>：{@code harness} 改名 {@code infrastructure}（命名层：名字要指它是什么）；
 * 原 {@code service} 面（执行 / 网络 / 附身 / 状态）并入同一面——它与骨架**互相依赖**（骨架用网络的
 * {@code Serverbound*Packet}、参数与状态；服务用 {@code SmarterClientState}/{@code ParamStore}/
 * {@code SmarterClientService}），且同样"不挂在装配图上" ⟹ 按判据二者本就<b>是同一个面</b>；
 * 面内再按机制种类平级分类（assembly / runtime / persistence / param / debug / execution / network / possession / state）。
 * <p>
 * <b>锁定的两件事</b>：
 * <ol>
 *   <li>骨架面不得 import 任何**候选系统**的包——禁止前缀由
 *       {@link SiblingPlacementTest#SYSTEM_PACKAGES}（模式包清单的单一数据源）机械派生，不可能再漂移；</li>
 *   <li>骨架面不得定义插槽（{@code new NodeKey<>}）——模式清单只由各概念的 {@code *NodeKeys} 持有
 *       （"每层只决定其下一层"）。</li>
 * </ol>
 * <b>方向性</b>：反向（模式 → 骨架提供的契约，如 {@code SaveSlot}/{@code ParamStore}/
 * {@code ParamPanelProvider}/{@code DebugPanelProvider}/{@code PersistableProvider}）是**允许**的——
 * 那是骨架提供给模式的契约。中立面（{@code core}、mod 根的 {@code network}）不在本断言范围。
 * <p>
 * <b>字形</b>（设计原则5）：辅助方法 {@code void} + DPS 出参。
 */
class InfrastructureBoundaryTest {

    /** Java 生产根。 */
    private static final Path JAVA_BASE =
            Paths.get("src/main/java/com/github/magif1712/smarter_touhou_maids");
    /** 骨架面根（相对 {@link #JAVA_BASE}）。 */
    private static final Path INFRA_BASE = JAVA_BASE.resolve("features/smarter/infrastructure");
    /** 骨架面文件数基线（W24 抽取 31：assembly 16 + runtime 5 + persistence 6 + param 3 + debug 1；W48 并入原 service 面 27：execution 2 + network 10 + possession 14 + state 1 ⟹ 58）。 */
    private static final int INFRA_FILE_BASELINE = 58;

    private static final String SMARTER =
            "com.github.magif1712.smarter_touhou_maids.features.smarter.";

    /**
     * 具体模式实现包前缀（= 装配图上登记的各分支实现所在包）。
     * <p>
     * <b>为什么不再手写清单</b>（W46 修掉一处静默失效）：W43 把系统收进 {@code modes/} 面后，
     * 手写的 {@code SMARTER + "<系统>."} 前缀已经对不上真实包名（真实包是 {@code …modes.<系统>}），
     * 于是"骨架不得依赖具体模式实现"这条断言<b>永远不可能红</b>——安全网静默失效。
     * 现改为从 {@link SiblingPlacementTest#SYSTEM_PACKAGES}（模式包清单的单一数据源）机械派生：
     * 结构一动这里自动跟着动，不可能再漂移。
     * <p>
     * <b>不在禁止集内的</b>：各概念的<b>内涵包</b>（{@code modes/<概念>}）与 nn 概念的共享机制
     * （{@code modes/nn/{bnn,cnn}}）——它们是骨架<strong>允许</strong>对话的契约与共同实现，不是"具体模式实现"。
     */
    private static final List<String> MODE_IMPL_PREFIXES = modeImplPrefixes();

    /** 从模式包清单派生禁止前缀（{@code modes/<概念>/<系统>} → {@code …smarter.modes.<概念>.<系统>.}）。 */
    private static List<String> modeImplPrefixes() {
        List<String> prefixes = new ArrayList<>();
        for (String packagePath : SiblingPlacementTest.SYSTEM_PACKAGES.values()) {
            prefixes.add(SMARTER + packagePath.replace('/', '.') + ".");
        }
        return List.copyOf(prefixes);
    }

    @Test
    void 骨架面文件数不得低于基线() {
        List<Path> files = new ArrayList<>();
        collectJavaFiles(INFRA_BASE /*->*/, files);
        assertTrue(files.size() >= INFRA_FILE_BASELINE,
                "骨架面文件数 " + files.size() + " < 基线 " + INFRA_FILE_BASELINE
                        + "——请确认骨架没有被搬回模式包（模式面与骨架面不得混同）");
    }

    @Test
    void 骨架面不得依赖候选系统的实现() {
        List<Path> files = new ArrayList<>();
        collectJavaFiles(INFRA_BASE /*->*/, files);

        List<String> violations = new ArrayList<>();
        for (Path file : files) {
            List<String> imports = new ArrayList<>();
            collectImports(file /*->*/, imports);
            for (String imp : imports) {
                for (String prefix : MODE_IMPL_PREFIXES) {
                    if (imp.startsWith(prefix)) {
                        violations.add(JAVA_BASE.relativize(file).toString().replace('\\', '/')
                                + "\n      → " + imp);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "骨架面依赖了候选系统的实现（骨架只应与内涵对话、不与外延耦合）——\n"
                        + "若确需该依赖，先判断它是'契约'还是'实现'：契约请移到中立面，实现说明分层有误：\n"
                        + String.join("\n", violations));
    }

    @Test
    void 骨架面不得定义插槽() {
        List<Path> files = new ArrayList<>();
        collectJavaFiles(INFRA_BASE /*->*/, files);

        List<String> violations = new ArrayList<>();
        for (Path file : files) {
            List<String> lines = new ArrayList<>();
            collectLines(file /*->*/, lines);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("new NodeKey<")) {
                    violations.add(JAVA_BASE.relativize(file).toString().replace('\\', '/')
                            + ":" + (i + 1) + "  " + lines.get(i).trim());
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "骨架面不得定义插槽（模式清单只由各层 *NodeKeys 持有，'每层只决定其下一层'）：\n"
                        + String.join("\n", violations));
    }

    /** 收集 {@code base} 下全部 {@code .java} 文件。 */
    private static void collectJavaFiles(Path base /*->*/, List<Path> out) {
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 收集该文件的 import 全名（已去掉 {@code import}/{@code static}/{@code ;}）。 */
    private static void collectImports(Path file /*->*/, List<String> out) {
        try (Stream<String> lines = Files.lines(file)) {
            lines.map(String::trim)
                    .filter(line -> line.startsWith("import "))
                    .map(line -> line.substring("import ".length())
                            .replace("static ", "").replace(";", "").trim())
                    .forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 读该文件全部行。 */
    private static void collectLines(Path file /*->*/, List<String> out) {
        try {
            out.addAll(Files.readAllLines(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
