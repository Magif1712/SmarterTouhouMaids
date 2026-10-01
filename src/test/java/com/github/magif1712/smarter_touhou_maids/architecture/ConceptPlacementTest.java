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
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 概念分类校验（定理1(4) 手段③「按概念分类放置」+ 定理1(2)/(3)）。
 * <p>
 * <b>为什么需要本类</b>：{@code modes/<概念>/} 是**概念**的家——概念目录的<b>直接文件</b>是它的
 * <b>要素（内涵）</b>（插槽 id / 槽类型接口 / 工厂契约 / X-Y 载体），其<b>子目录</b>是它的
 * <b>外延</b>（候选系统）。"按概念分类"若只写在文档里就是不实在的（原则4），故把三件事实在化为断言：
 * <ol>
 *   <li><b>L1 概念清单</b>：{@code modes/} 的直接子项<b>恰好</b> = 7 个概念（+ 过渡期账本里的待归位系统）；</li>
 *   <li><b>L2 内涵不多不少</b>：每个概念包的直接文件 = 冻结的要素清单（多一个少一个都要回答
 *       "它属于这个概念的哪个位置"）；概念目录内的子包只允许是<b>该概念的内涵分类</b>
 *       （如 nn 的共享机制 {@code bnn}/{@code cnn}）或该概念的候选系统；</li>
 *   <li><b>L3 内涵不得依赖外延</b>：概念包的直接文件不得 import 任何候选系统包——定理1(3) 的方向
 *       （{@code Ext(S.Y) ⊆ Ext(T.Y)}：外延依赖内涵，反之不成立）。</li>
 * </ol>
 * <b>过渡账本</b>（{@link #PENDING_UNCLASSIFIED}）：未按概念归位的系统必须登记（<b>清单外的违规不许出现、
 * 修好必须删行</b>——双向锁，避免腐烂成永久 TODO）。这是仓库既有纪律（W29 的 {@code PENDING_NESTED}）。
 * <p>
 * <b>字形</b>（设计原则5）：辅助方法 {@code void} + DPS 出参。
 */
class ConceptPlacementTest {

    /** smarter 域根。 */
    private static final Path SMARTER = Paths.get(
            "src/main/java/com/github/magif1712/smarter_touhou_maids/features/smarter");
    /** 模式面根（相对 {@link #SMARTER}）。 */
    private static final Path MODES = SMARTER.resolve("modes");

    /**
     * 7 个概念 = 装配图上 7 个"层"的类型契约所在（{@code agent}…{@code nn}）。
     * <p>
     * 注意：概念 ≠ 插槽。{@code nn} 是一个概念，却对应<b>三个</b> per-mapper 插槽
     * （{@code original_mapper_nn}/{@code bnn_mapper_nn}/{@code cnn_active_mapper_nn}），
     * 因为"每层只决定其下一层"——插槽 id 由 mapper 层持有，而 nn 的类型契约是共享的。
     */
    private static final List<String> CONCEPTS = List.of(
            "agent", "ai", "effector", "mapper", "nn", "process", "sensor");

    /**
     * 冻结的要素清单（内涵）：概念包的直接文件。
     * <p>
     * 与 {@link ContractMinimalityTest} 的分工：那边锁接口的<b>强制面</b>（成员），这边锁
     * <b>要素集合</b>（有哪些文件）。删掉一个要素 = 概念少了一块；新增一个 = 必须说明它是
     * 插槽 id / 类型接口 / 工厂契约 / X-Y 载体中的哪一类。
     */
    private static final Map<String, List<String>> CONCEPT_ELEMENTS = Map.ofEntries(
            Map.entry("agent", List.of("AgentFactory.java", "AgentNodeKeys.java", "IAgent.java")),
            Map.entry("ai", List.of("AiFactory.java", "IAiSystem.java", "ProcessAiNodeKeys.java")),
            Map.entry("effector", List.of("ActionIntent.java", "EffectorFactory.java", "IEffector.java")),
            Map.entry("mapper", List.of("FittableMapper.java", "FittableMapperFactory.java",
                    "NnNodeKeys.java", "VisionEncoder.java")),
            Map.entry("nn", List.of("INeuralNetwork.java", "NnEncodingProfile.java", "NnFactory.java")),
            Map.entry("process", List.of("IProcessSystem.java", "ProcessFactory.java",
                    "UranaProcessNodeKeys.java")),
            Map.entry("sensor", List.of("IPullEncodedSensor.java", "IPushEncodedSensor.java",
                    "ISensor.java", "SensorFactory.java")));

    /**
     * 概念目录内的<b>内涵子包</b>：该概念的共享实现按家族分类（手段②"因组件太多而分类"）。
     * <p>
     * W46：nn 的共享机制（原顶层 {@code nn/} 机制面）归入此列——它只被<b>同概念</b>的候选共享，
     * 因此是内涵而非"独立机制面"；判据的事实前提已核验（{@code AbstractBnnNeuralNetwork} 被
     * {@code original_bnn} 与 {@code standard_bnn} 共同依赖，{@code AbstractCnnNeuralNetwork}
     * 被 {@code original_cnn} 与 {@code cnn_active} 共同依赖）。
     * <p>
     * <b>W46 的第二个发现</b>（新锁 L3 落地时立刻抓到）：mapper 概念的 X-Y 载体
     * （{@code IODomain}/{@code InputVectorDomain}/{@code OutputVectorDomain} 及其 {@code subspan}）
     * 此前被塞在 <b>urana 系统包</b>里（{@code modes/urana/semantics/containers/io/}），
     * 却被 mapper 概念（{@code FittableMapper}/{@code FittableMapperFactory}）与<b>三个</b> mapper 候选
     * 共同使用——正是 W31 判定过的"要素不能塞进任一具体系统的包"。已归位为 {@code modes/mapper/io/}，
     * 与同为 X-Y 载体的 {@code VisionEncoder} 同居概念之家。
     */
    private static final Map<String, List<String>> CONCEPT_INNER_PACKAGES = Map.ofEntries(
            Map.entry("mapper", List.of("io")),
            Map.entry("nn", List.of("bnn", "cnn")));

    /**
     * 过渡账本：尚未按概念归位的系统目录名（相对 {@code modes/}）。
     * <p>
     * <b>双向锁</b>：清单外的系统若仍未归位 ⟹ 断言红；清单内的若已归位 ⟹ 断言要求删行。
     * W46 的批 2 已完成 13 个系统的归位，故本清单<b>清零</b>；字段保留以维持这条机制——
     * 将来任何新系统必须先按概念归位，未归位者必须在此登记（不许静默出现）。
     */
    private static final List<String> PENDING_UNCLASSIFIED = List.of(); // W46 批 2：13 个系统已全部归位

    @Test
    void modes的直接子项恰好是概念与已登记的待归位系统() {
        List<String> actual = new ArrayList<>();
        collectDirectDirNames(MODES /*->*/, actual);

        List<String> expected = new ArrayList<>(CONCEPTS);
        expected.addAll(PENDING_UNCLASSIFIED);
        expected.sort(String::compareTo);

        List<String> problems = new ArrayList<>();
        for (String name : actual) {
            if (!expected.contains(name)) {
                problems.add("modes/" + name + "：既不是概念目录，也不在待归位账本里"
                        + "（定理1(4) 手段③：模式面只能按概念分类）");
            }
        }
        for (String name : expected) {
            if (!actual.contains(name)) {
                problems.add("modes/" + name + "：已登记但目录不存在——请同步清单"
                        + "（概念被删/搬走？待归位项已归位？）");
            }
        }
        assertTrue(problems.isEmpty(),
                "概念分类被破坏（定理1(4) 手段③ + 原理4：清单必须与实物同步）：\n"
                        + String.join("\n", problems));
    }

    @Test
    void 概念包的要素清单必须与冻结清单一致() {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : CONCEPT_ELEMENTS.entrySet()) {
            String concept = entry.getKey();
            TreeSet<String> actual = new TreeSet<>();
            collectDirectJavaFileNames(MODES.resolve(concept) /*->*/, actual);

            TreeSet<String> expected = new TreeSet<>(entry.getValue());
            if (!expected.equals(actual)) {
                problems.add("modes/" + concept + "/：要素（内涵）集合与冻结清单不一致——\n"
                        + "      期望: " + expected + "\n      实际: " + actual + "\n"
                        + "      新增要素前先回答：它是插槽 id / 类型接口 / 工厂契约 / X-Y 载体中的哪一类？"
                        + "若它是<b>实现</b>，它属于候选系统（外延），不属于概念包。");
            }

            List<String> innerAllowed = new ArrayList<>(
                    CONCEPT_INNER_PACKAGES.getOrDefault(concept, List.of()));
            List<String> subDirs = new ArrayList<>();
            collectDirectDirNames(MODES.resolve(concept) /*->*/, subDirs);
            for (String sub : subDirs) {
                if (!isElementOfConcept(sub, concept, innerAllowed)) {
                    problems.add("modes/" + concept + "/" + sub + "：概念目录内的子包只能是"
                            + "该概念的内涵分类 " + innerAllowed + " 或该概念的候选系统——请归位或登记");
                }
            }
        }
        assertTrue(problems.isEmpty(),
                "概念的要素与内涵分类不符（定理1(2)：内涵与外延不多不少地各居其位）：\n"
                        + String.join("\n", problems));
    }

    @Test
    void 内涵不得依赖外延() {
        List<String> problems = new ArrayList<>();
        for (String concept : CONCEPTS) {
            List<Path> files = new ArrayList<>();
            collectIntrinsicJavaFiles(MODES.resolve(concept),
                    CONCEPT_INNER_PACKAGES.getOrDefault(concept, List.of()) /*->*/, files);
            for (Path file : files) {
                List<String> imports = new ArrayList<>();
                collectImports(file /*->*/, imports);
                for (String imp : imports) {
                    for (String prefix : modeImplPrefixes()) {
                        if (imp.startsWith(prefix)) {
                            problems.add(SMARTER.relativize(file).toString().replace('\\', '/')
                                    + "\n      → " + imp + "（概念包不得依赖候选系统的实现）");
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(),
                "内涵依赖了外延（违反定理1(3)：Ext(S.Y) ⊆ Ext(T.Y)，外延依赖内涵，反之不成立）——\n"
                        + "概念包只应依赖：自身要素、其它概念的内涵、装配机制：\n"
                        + String.join("\n", problems));
    }
    /** 该子包名是否属于概念 {@code concept} 的内涵分类或其候选系统。 */
    private static boolean isElementOfConcept(String sub, String concept, List<String> innerAllowed) {
        if (innerAllowed.contains(sub)) {
            return true;
        }
        for (String packagePath : SiblingPlacementTest.SYSTEM_PACKAGES.values()) {
            String[] segments = packagePath.split("/");
            if (segments.length >= 2
                    && segments[segments.length - 2].equals(concept)
                    && segments[segments.length - 1].equals(sub)) {
                return true;
            }
        }
        return false;
    }

    /** 从模式包清单派生"具体模式实现"的包前缀（与 {@link InfrastructureBoundaryTest} 同源，避免清单漂移）。 */
    private static List<String> modeImplPrefixes() {
        List<String> prefixes = new ArrayList<>();
        for (String packagePath : SiblingPlacementTest.SYSTEM_PACKAGES.values()) {
            prefixes.add("com.github.magif1712.smarter_touhou_maids.features.smarter."
                    + packagePath.replace('/', '.') + ".");
        }
        return prefixes;
    }

    /**
     * 收集某概念的<b>内涵</b>文件（= 要素文件 + 内涵子包内的全部文件）。
     * <p>
     * 内涵 = 该概念的契约与共同实现，它们都不得依赖外延；故 L3 的扫描面是"内涵全域"，
     * 而不只是概念包的直接文件。
     *
     * @param conceptDir    概念包目录
     * @param innerPackages 该概念的内涵子包名（如 nn 的 {@code bnn}/{@code cnn}、mapper 的 {@code io}）
     */
    private static void collectIntrinsicJavaFiles(Path conceptDir, List<String> innerPackages,
            /*->*/ List<Path> out) {
        collectDirectJavaFiles(conceptDir /*->*/, out);
        for (String inner : innerPackages) {
            try (Stream<Path> walk = Files.walk(conceptDir.resolve(inner))) {
                walk.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .sorted()
                        .forEach(out::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** 收集 {@code base} 的直接子目录名。 */
    private static void collectDirectDirNames(Path base /*->*/, List<String> out) {
        try (Stream<Path> list = Files.list(base)) {
            list.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 收集 {@code base} 下的直接 {@code .java} 文件名。 */
    private static void collectDirectJavaFileNames(Path base /*->*/, TreeSet<String> out) {
        List<Path> files = new ArrayList<>();
        collectDirectJavaFiles(base /*->*/, files);
        for (Path file : files) {
            out.add(file.getFileName().toString());
        }
    }

    /** 收集 {@code base} 下的直接 {@code .java}（不递归）。 */
    private static void collectDirectJavaFiles(Path base /*->*/, List<Path> out) {
        try (Stream<Path> list = Files.list(base)) {
            list.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 收集该文件的 import 全名（已去掉 {@code import}/{@code static}/{@code ;}）。 */
    private static void collectImports(Path file /*->*/, List<String> out) {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            lines.map(String::trim)
                    .filter(line -> line.startsWith("import "))
                    .map(line -> line.substring("import ".length())
                            .replace("static ", "").replace(";", "").trim())
                    .forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

