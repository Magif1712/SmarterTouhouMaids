package com.github.magif1712.smarter_touhou_maids.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 插槽归属校验（R6）：<b>谁定义插槽 id，就只能由那一层创建该插槽</b>。
 * <p>
 * <b>依据</b>（真善美第2条 + 定理1(2)/(4)）：父层只决定<b>直接</b>下层的插槽；更下层的插槽由那层
 * 自己（或其分支）创建。越级装配（如 process 层隔两层创建 nn 层插槽）会让层级边界被跨层写入——
 * 附属模组或新分支就无法"只碰自己那一层"，而这正是"每层可独立替换"的前提。
 * <p>
 * <b>历史</b>：W28 前 {@code urana/UranaProcessRegistration} 创建了 {@code NnNodeKeys} 的两个
 * per-mapper NN 插槽（process 层 → nn 层，隔两层），而同类的 {@code CnnActiveRegistration}
 * （mapper 层）创建自己的 NN 插槽——同一件事两种层级。W28 归位为 mapper 层自注册
 * （{@code OriginalMapperRegistration}/{@code BnnMapperRegistration}），本断言锁住。
 * <p>
 * <b>字形</b>（设计原则5）：辅助方法 {@code void} + DPS 出参。
 */
class SlotOwnershipTest {

    /** 生产 Java 根。 */
    private static final Path MAIN_JAVA = Paths.get("src/main/java");
    /** 模组包根（清单里的目录前缀以它为基准）。 */
    private static final Path BASE = MAIN_JAVA.resolve("com/github/magif1712/smarter_touhou_maids");

    /**
     * 插槽（{@code Keys.CONST}）→ <b>允许创建</b>它的目录（相对 {@link #BASE}）。
     * <p>
     * <b>判据</b>：允许的创建者 = ①持有该插槽 id 的层 ∪ ②该插槽<b>候选分支</b>所在的层 ∪
     * ③<b>声明该插槽为 child</b> 的分支所在的层。三者都是"那一层"，越出三者即越级装配。
     * <p>
     * <b>两种记法</b>（W46 起，见 {@link #allows}）——这不是审美，是"概念不外溢"的表达：
     * <ul>
     *   <li>{@code "…/<系统>/"}（尾斜杠）= <b>整棵树</b>：候选系统 / 声明它的分支所在的层，其子包都算"那一层"；</li>
     *   <li>{@code "…/<概念>"}（无尾斜杠）= <b>仅该目录的直接文件</b>：概念包是"层"本身（持有插槽 id），
     *       它<strong>不外溢</strong>到自己的外延（候选系统子目录）。</li>
     * </ul>
     * 依据：定理1(4) 手段③（按概念分类）+ 定理1(3)（外延依赖内涵，内涵不依赖外延）。若用普通前缀匹配，
     * 概念目录会成为其下<strong>一切</strong>候选系统的通行证，越级装配将静默通过。
     * <p>
     * 逐条来历（便于审查）：
     * <ul>
     *   <li>{@code AGENT}：id 在 agent 层；候选 {@code smarter} 与声明者同属 agent 层 ⟹ agent。</li>
     *   <li>{@code AI}：id 在 agent 层（父层决定）；候选 {@code process_ai} 属 ai 层 ⟹ 两者都允许。</li>
     *   <li>{@code SENSOR}/{@code EFFECTOR}：id 与声明者（smarter 分支）在 agent 层；候选在 sensor/effector 层。</li>
     *   <li>{@code PROCESS}：id 在 ai 层；候选 {@code urana} 属 process 层；声明者 {@code process_ai} 属 ai 层。</li>
     *   <li>{@code MAPPER}：id 在 process 层；候选三个 mapper 属 mapper 层；声明者 {@code urana} 属 process 层。</li>
     *   <li>{@code NnNodeKeys.*}：id 在 mapper 层；候选（cnn/bnn/standard_bnn/cnn_active）与声明者（各 mapper 分支）
     *       都在 mapper 层 ⟹ <b>只允许 mapper 层</b>（W28 前 urana 越级创建即在此被抓）。</li>
     *   <li>{@code CONFIG_GUI}：id、候选、声明者都在 UI 层。</li>
     * </ul>
     */
    private static final Map<String, List<String>> ALLOWED_CREATORS = Map.ofEntries(
            Map.entry("AgentNodeKeys.AGENT", List.of("features/smarter/modes/agent/reflex_arc_system_agent/", "features/smarter/modes/agent")),
            Map.entry("AgentNodeKeys.AI", List.of(
                    "features/smarter/modes/agent", "features/smarter/modes/ai", "features/smarter/modes/ai/process_ai/")),
            Map.entry("AgentNodeKeys.SENSOR", List.of(
                    "features/smarter/modes/agent/reflex_arc_system_agent/", "features/smarter/modes/agent", "features/smarter/modes/sensor/possession_sensor/", "features/smarter/modes/sensor/on_demand_possession_sensor/")),
            Map.entry("AgentNodeKeys.EFFECTOR", List.of(
                    "features/smarter/modes/agent/reflex_arc_system_agent/", "features/smarter/modes/agent", "features/smarter/modes/effector/bionic_muscle_effector/")),
            Map.entry("ProcessAiNodeKeys.PROCESS", List.of(
                    "features/smarter/modes/ai", "features/smarter/modes/ai/process_ai/",
                    "features/smarter/modes/process", "features/smarter/modes/process/urana/")),
            Map.entry("UranaProcessNodeKeys.MAPPER", List.of(
                    "features/smarter/modes/process", "features/smarter/modes/process/urana/",
                    "features/smarter/modes/mapper/original_mapper/", "features/smarter/modes/mapper/bnn_mapper/", "features/smarter/modes/mapper/cnn_active_mapper/")),
            Map.entry("NnNodeKeys.ORIGINAL_MAPPER_NN", List.of("features/smarter/modes/mapper/original_mapper/")),
            Map.entry("NnNodeKeys.BNN_MAPPER_NN", List.of("features/smarter/modes/mapper/bnn_mapper/", "features/smarter/modes/nn/standard_bnn/")),
            Map.entry("NnNodeKeys.CNN_ACTIVE_MAPPER_NN", List.of("features/smarter/modes/mapper/cnn_active_mapper/")),
            Map.entry("ConfigGuiIds.CONFIG_GUI", List.of("features/ui/")));

    /** 必须存在的插槽创建（= 装配图上的全部插槽，与 {@code ArchitectureFacts.NODE_PATHS} 对应）。 */
    private static final List<String> EXPECTED_SLOTS = List.of(
            "AgentNodeKeys.AGENT", "AgentNodeKeys.AI",
            "AgentNodeKeys.SENSOR", "AgentNodeKeys.EFFECTOR",
            "ProcessAiNodeKeys.PROCESS", "UranaProcessNodeKeys.MAPPER",
            "NnNodeKeys.ORIGINAL_MAPPER_NN", "NnNodeKeys.BNN_MAPPER_NN",
            "NnNodeKeys.CNN_ACTIVE_MAPPER_NN", "ConfigGuiIds.CONFIG_GUI");

    /** {@code .node(XxxNodeKeys.CONST)} 形式的建槽调用。 */
    private static final Pattern NODE_CREATE = Pattern.compile("\\.node\\(([A-Za-z_]\\w*)\\.([A-Z]\\w*)\\)");

    @Test
    void 插槽只能由定义它的那一层创建() {
        Map<String, List<String>> creators = new LinkedHashMap<>();
        collectCreators(BASE /*->*/, creators);

        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : creators.entrySet()) {
            List<String> allowed = ALLOWED_CREATORS.get(entry.getKey());
            if (allowed == null) {
                violations.add(entry.getKey() + " —— 未登记的插槽，请在 ALLOWED_CREATORS 登记其归属层");
                continue;
            }
            for (String file : entry.getValue()) {
                if (!allows(file, allowed)) {
                    violations.add(entry.getKey() + " 被 " + file + " 创建——该层不允许创建此插槽"
                            + "（允许：" + allowed + "）");
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "越级装配（R6）：插槽只能由'决定它的那一层'创建，否则层边界被跨层写入——\n"
                        + String.join("\n", violations));
    }

    @Test
    void 每个插槽都必须有创建者() {
        Map<String, List<String>> creators = new LinkedHashMap<>();
        collectCreators(BASE /*->*/, creators);

        List<String> missing = new ArrayList<>();
        for (String slot : EXPECTED_SLOTS) {
            if (!creators.containsKey(slot)) {
                missing.add(slot);
            }
        }
        assertTrue(missing.isEmpty(),
                "以下插槽在装配图里没有任何创建者（漏建 / 被搬迁时丢掉）：\n"
                        + String.join("\n", missing));
    }

    /** 收集全部 {@code .node(Keys.CONST)} 建槽点：{@code "Keys.CONST"} → 相对 {@link #BASE} 的文件路径。 */
    private static void collectCreators(Path root /*->*/, Map<String, List<String>> out) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path file : files) {
            String relative = BASE.relativize(file).toString().replace('\\', '/');
            boolean inDoc = false;
            for (String raw : readLines(file)) {
                // 注释感知（陷阱1 同族：javadoc 里的代码示例曾把文本扫描器骗成"真实注册点"）
                boolean startsDoc = raw.contains("/**");
                if (inDoc) {
                    if (raw.contains("*/")) {
                        inDoc = false;
                    }
                    continue;
                }
                if (startsDoc) {
                    inDoc = !raw.contains("*/");
                    continue;
                }
                String line = raw;
                int commentAt = line.indexOf("//");
                if (commentAt >= 0) {
                    line = line.substring(0, commentAt);
                }
                String trimmed = line.trim();
                if (trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                    continue;
                }
                Matcher m = NODE_CREATE.matcher(line);
                while (m.find()) {
                    out.computeIfAbsent(m.group(1) + "." + m.group(2), k -> new ArrayList<>()).add(relative);
                }
            }
        }
    }

    /**
     * 该文件是否落在允许的创建者目录内（两种记法见 {@link #ALLOWED_CREATORS} 的 javadoc）。
     * <p>
     * <b>为什么不能用 {@code startsWith} 一把梭</b>：按概念分类后，概念包是候选系统包的<b>父目录</b>
     * （{@code modes/mapper} ⊃ {@code modes/mapper/original_mapper}），普通前缀匹配会让
     * "越级装配（R6）"这条锁静默失效——它只能靠"目录恰好等于"来区分内涵与外延。
     *
     * @param relativeFile 相对 {@link #BASE} 的文件路径（{@code /} 分隔）
     * @param allowedDirs  该插槽允许的目录清单（尾斜杠 = 整棵树；无尾斜杠 = 仅直接文件）
     */
    private static boolean allows(String relativeFile, List<String> allowedDirs) {
        int at = relativeFile.lastIndexOf('/');
        if (at < 0) {
            return false;
        }
        String parent = relativeFile.substring(0, at);
        for (String entry : allowedDirs) {
            if (entry.endsWith("/")) {
                String root = entry.substring(0, entry.length() - 1);
                if (parent.equals(root) || parent.startsWith(entry)) {
                    return true;
                }
            } else if (parent.equals(entry)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
