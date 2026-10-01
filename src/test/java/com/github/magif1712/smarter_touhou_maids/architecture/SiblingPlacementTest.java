package com.github.magif1712.smarter_touhou_maids.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平级放置校验（定理1(4)）：<b>每个映射系统（= 装配图上的分支实现）的包必须与其它系统平级</b>。
 * <p>
 * <b>依据</b>：(4) 的三种放置办法 - W43 用"因组件太多而分类"收成 {@code modes/} 面，W46 用
 * "**按概念分类**"把每个系统挂到它所属的概念之下：{@code modes/<概念>/<系统>}。概念是内涵（要素）与外延
 * （候选系统）的共同居所，故"平级"的判据随之收紧为：<b>同一概念的候选彼此平级，且不得再嵌套</b>
 * （同一槽的候选正是"换一个实现动一处"的对象）；跨概念的关系（S 与 T 的串联、A 在上层）仍由
 * **装配图**（{@code ConceptTree}）表达，<b>不由目录嵌套表达</b>。
 * <p>
 * <b>为什么写成"账本"而不是直接断言</b>（真善美第4条）：W24 复核发现链上仍有 8/12 对相邻系统
 * 不平级（{@code mapper/*}、各 {@code *_mapper/nn/*}、{@code sensor/*}、{@code effector/*}
 * 仍嵌在深层）。一次性搬完风险太大，故把"待整改"实在化为 {@link #PENDING_NESTED}：
 * <b>清单外的违规不许出现</b>（不许新增嵌套）、<b>清单内的一旦修好必须删除</b>（不许腐烂成永久 TODO）。
 * <p>
 * <b>字形</b>（设计原则5）：辅助方法 {@code void} + DPS 出参。
 */
class SiblingPlacementTest {

    /** smarter 域根。 */
    private static final Path SMARTER = Paths.get(
            "src/main/java/com/github/magif1712/smarter_touhou_maids/features/smarter");

    /**
     * 13 个系统（装配图上的分支实现）→ 目标包（{@code features/smarter/modes/<概念>/} 下的目录名）。
     * <p>
     * 取值依据：{@code ArchitectureFacts.BRANCHES_BY_NODE} 的分支 id，叠加
     * {@code BranchIdentityTest} 登记的**领域名例外**（{@code smarter}→{@code reflex_arc_system_agent}、
     * {@code cnn}→{@code original_cnn}、{@code bnn}→{@code original_bnn}）。
     * <p>
     * <b>本字段是"模式包清单"的单一数据源</b>：{@link TopLevelShapeTest} 直接复用它校验顶层形态，
     * 避免两份清单漂移。
     */
    static final Map<String, String> SYSTEM_PACKAGES = Map.ofEntries(
            Map.entry("smarter", "modes/agent/reflex_arc_system_agent"),
            Map.entry("process_ai", "modes/ai/process_ai"),
            Map.entry("urana", "modes/process/urana"),
            Map.entry("original_mapper", "modes/mapper/original_mapper"),
            Map.entry("bnn_mapper", "modes/mapper/bnn_mapper"),
            Map.entry("cnn_active_mapper", "modes/mapper/cnn_active_mapper"),
            Map.entry("cnn", "modes/nn/original_cnn"),
            Map.entry("bnn", "modes/nn/original_bnn"),
            Map.entry("standard_bnn", "modes/nn/standard_bnn"),
            Map.entry("cnn_active", "modes/nn/cnn_active"),
            Map.entry("possession_sensor", "modes/sensor/possession_sensor"),
            Map.entry("on_demand_possession_sensor", "modes/sensor/on_demand_possession_sensor"),
            Map.entry("bionic_muscle_effector", "modes/effector/bionic_muscle_effector"));

    /**
     * 待整改账本（W30 冻结）：仍未平级的系统包（相对 {@link #SMARTER} 的嵌套路径）。
     * <p>
     * 目标位置都是 {@code features/smarter/modes/<概念>/<系统>}（W46 起按概念分类）。**修好一个就从这里删一行**；W46 批 2 已清零，字段保留以维持双向锁机制。
     * <p>
     * 注意：{@code nn/bnn/standard_bnn}（机制包，住 {@code ElasticOpsNative}）<b>不在</b>本账本——
     * 它不是系统（不挂在装配图上），它的问题是 R1（文件错位）。
     */
    private static final List<String> PENDING_NESTED = List.of();

    /** 同名但<b>不是系统</b>的机制包（豁免后缀匹配；R1 修好后该项应消失，届时删掉本豁免）。 */
    private static final List<String> NON_SYSTEM_SAME_NAME = List.of(); // W36：R1 修好后已无同名机制包

    @Test
    void 系统包必须平级或登记在待整改账本() {
        List<String> flatNames = new ArrayList<>();
        List<String> nestedPaths = new ArrayList<>();
        collectPackageDirs(SMARTER /*->*/, flatNames, nestedPaths);

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> entry : SYSTEM_PACKAGES.entrySet()) {
            String target = entry.getValue();
            boolean flat = flatNames.contains(target) || nestedPaths.contains(target); // target 可为多级路径（如 modes/process/urana）
            List<String> nested = new ArrayList<>();
            for (String path : nestedPaths) {
                if (path.endsWith("/" + target) && !NON_SYSTEM_SAME_NAME.contains(path)) {
                    nested.add(path);
                }
            }
            if (!flat && nested.isEmpty()) {
                problems.add(entry.getKey() + " → " + target
                        + "：系统包既不在 features/smarter/ 下，也不在任何嵌套位置——包被删/搬丢了？");
            }
            for (String path : nested) {
                if (!PENDING_NESTED.contains(path)) {
                    problems.add(path + "：系统包未平级且未登记到 PENDING_NESTED"
                            + "（定理1(4)：S 与 T 的包必须平级放置）");
                }
            }
            if (flat && PENDING_NESTED.contains(target)) {
                problems.add(target + "：已平级，请从 PENDING_NESTED 删除该登记");
            }
        }
        for (String pending : PENDING_NESTED) {
            if (!nestedPaths.contains(pending)) {
                problems.add(pending + "：账本项已不存在（已搬迁？），请从 PENDING_NESTED 删除该行");
            }
        }
        for (String exempt : NON_SYSTEM_SAME_NAME) {
            if (!nestedPaths.contains(exempt)) {
                problems.add(exempt + "：同名机制包已不存在，请从 NON_SYSTEM_SAME_NAME 删除该豁免");
            }
        }
        assertTrue(problems.isEmpty(),
                "平级放置（定理1(4)）被破坏——系统包必须与其它系统同级，未达标者须登记：\n"
                        + String.join("\n", problems));
    }

    /** 收集 {@code base} 下的包目录（= 直接含 .java 的目录）：直接子目录名 + 更深的相对路径。 */
    private static void collectPackageDirs(Path base /*->*/, List<String> flat, List<String> nested) {
        List<Path> dirs = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isDirectory).sorted().forEach(dirs::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path dir : dirs) {
            String relative = base.relativize(dir).toString().replace('\\', '/');
            if (relative.isEmpty() || !containsJavaFile(dir)) {
                continue;
            }
            if (relative.contains("/")) {
                nested.add(relative);
            } else {
                flat.add(relative);
            }
        }
    }

    /** 该目录直接含 {@code .java}（= 它本身是一个包）。 */
    private static boolean containsJavaFile(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(path -> Files.isRegularFile(path)
                    && path.toString().endsWith(".java"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
