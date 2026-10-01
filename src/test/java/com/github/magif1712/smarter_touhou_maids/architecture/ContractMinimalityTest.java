package com.github.magif1712.smarter_touhou_maids.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 契约最小性校验（R4）：概念接口的<b>强制面</b>（抽象方法集合）必须与冻结清单一致。
 * <p>
 * <b>为什么需要本类</b>（真善美第2条 + 定理1(4) 的"要素"侧）：插槽是**概念**（其外延 = 该槽全部
 * 候选系统），而概念接口里的抽象方法 = **强制每个候选实现**的方法。若把"只对部分候选有意义"的
 * 方法声明为抽象方法，契约的外延就不再等于各候选三要素的共同面：被迫实现无意义方法的候选
 * 带来认知成本（不善），"换实现零改动"也会变成"换实现先补一个空壳"。
 * <p>
 * <b>R4 的两个方向</b>：
 * <ol>
 *   <li>非共同面必须走 <b>default 可选能力</b>（项目既有模式：{@code setRefreshRequest}/
 *       {@code setVisionEncoder}/{@code newFeelingBuffer}/…）；</li>
 *   <li>清单外的抽象方法<b>不许出现</b>：新增抽象方法 = 契约形态变更 → 本断言变红 → 强制在一次
 *       提交里回答"它对<b>全部</b>候选（含未来的纯规则 ai、单环 process）都共同吗"。</li>
 * </ol>
 * <b>清单来源</b>（W26）：从生产源码机械提取（接口体第一层的成员），并逐个复核"它为什么是强制的"。
 * 历史：{@code IAiSystem}/{@code IProcessSystem} 的 {@code setDtDebugEnabled} 曾是<b>抽象</b>方法
 * （urana 的私有诊断模式冒充共同面，见重构记录 §八 R4），W26 收口为 default 可选能力。
 * <p>
 * <b>字形</b>（设计原则5）：辅助方法 {@code void} + DPS 出参。
 */
class ContractMinimalityTest {

    /** smarter 域根（清单里的键以它为基准）。 */
    private static final Path SMARTER_BASE = Paths.get(
            "src/main/java/com/github/magif1712/smarter_touhou_maids/features/smarter");

    /** 接口体第一层的成员声明（本项目成员统一 4 空格缩进；名字 = 第一个 {@code (} 前的标识符）。 */
    private static final Pattern MEMBER = Pattern.compile(
            "^ {4}(?:(default|static|private|protected) )?[^=;]*?([A-Za-z_]\\w*)\\s*\\(");

    /** 冻结的强制面：接口文件（相对 {@link #SMARTER_BASE}）→ 该接口声明的全部成员。 */
    private static final Map<String, List<String>> FORCED_SURFACE = frozenSurface();

    private static Map<String, List<String>> frozenSurface() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("modes/agent/IAgent.java", List.of(
                "abstract awaken", "abstract onClientTick", "abstract onPostRender",
                "abstract save", "abstract shutdown",
                "default isActive"));
        m.put("modes/ai/IAiSystem.java", List.of(
                "abstract awaken", "abstract behaviorSize", "abstract feelingSize",
                "abstract save", "abstract shutdown",
                "default newBehaviorBuffer", "default newFeelingBuffer", "default newVisionEncoder",
                "default readBehaviorTo", "default setDtDebugEnabled", "default setRefreshRequest"));
        m.put("modes/process/IProcessSystem.java", List.of(
                "abstract awaken", "abstract behaviorSize", "abstract feelingSize",
                "abstract save", "abstract shutdown",
                "default newBehaviorBuffer", "default newFeelingBuffer", "default newVisionEncoder",
                "default readBehaviorTo", "default setDtDebugEnabled", "default setRefreshRequest"));
        m.put("modes/mapper/FittableMapper.java", List.of(
                "abstract assembleT", "abstract assembleX", "abstract bw", "abstract createFwTraceForBw",
                "abstract createGradientVector", "abstract createVector", "abstract extractC",
                "abstract extractF", "abstract fw", "abstract getHyperparameters",
                "abstract getInputDomain", "abstract getOutputDomain", "abstract loadGradientVector",
                "abstract loadVector", "abstract save", "abstract zeroGradient", "abstract zeroVector",
                "default newBehaviorBuffer", "default newFeelingBuffer", "default newVisionEncoder",
                "default readBehaviorTo"));
        m.put("modes/nn/INeuralNetwork.java", List.of(
                "abstract backward", "abstract computeOutputGradient", "abstract copyFromInput",
                "abstract copyFromInputGradient", "abstract copyFromOutput", "abstract copyToInput",
                "abstract copyToInputFromHost", "abstract copyToInputFromLong", "abstract createFwTraceForBw",
                "abstract createGradientVector", "abstract createVector", "abstract encodingProfile",
                "abstract forward", "abstract getHyperparameters", "abstract gradientToInput",
                "abstract gradientToInputFromInternal", "abstract injectOutputGradient",
                "abstract injectOutputGradientFromInputGradient", "abstract loadGradientVector",
                "abstract loadVector", "abstract save", "abstract setTarget", "abstract zeroGradient",
                "abstract zeroVector",
                "default newBehaviorBuffer", "default newFeelingBuffer", "default readBehaviorTo"));
        m.put("modes/nn/NnFactory.java", List.of(
                "abstract create", "abstract encodingProfile",
                "default create", "default producedType"));
        m.put("modes/sensor/ISensor.java", List.of(
                "abstract awaken", "abstract capture", "abstract shutdown",
                "default setRefreshRequest", "default setVisionEncoder"));
        m.put("modes/effector/IEffector.java", List.of(
                "abstract awaken", "abstract shutdown", "abstract tick"));
        return m;
    }

    @Test
    void 概念接口的强制面必须与冻结清单一致() {
        for (Map.Entry<String, List<String>> entry : FORCED_SURFACE.entrySet()) {
            List<String> actual = new ArrayList<>();
            collectDeclaredMembers(SMARTER_BASE.resolve(entry.getKey()) /*->*/, actual);
            List<String> expected = new ArrayList<>(entry.getValue());
            Collections.sort(expected);
            Collections.sort(actual);
            assertEquals(expected, actual,
                    entry.getKey() + " 的声明成员与冻结清单不一致——\n"
                            + "    新增/删除抽象方法前先回答：它对**全部**候选（含未来的纯规则 ai、单环 process）"
                            + "都共同吗？非共同面请改为 default 可选能力（R4），然后同步本清单。\n"
                            + "    期望: " + expected + "\n"
                            + "    实际: " + actual);
        }
    }

    /**
     * 收集该接口文件在接口体第一层声明的成员（{@code abstract/default/static/private name}）。
     * <p>
     * 跳过 javadoc / 注释行；花括号深度按行累计（本项目成员签名都是单行，方法体不会造成误判）。
     */
    private static void collectDeclaredMembers(Path file /*->*/, List<String> out) {
        List<String> lines = readLines(file);
        int depth = 0;
        boolean inDoc = false;
        for (String line : lines) {
            boolean startsDoc = line.contains("/**");
            boolean endsDoc = line.contains("*/");
            if (inDoc) {
                if (endsDoc) {
                    inDoc = false;
                }
                continue;
            }
            if (startsDoc) {
                inDoc = !endsDoc;
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }
            int depthBefore = depth;
            depth += count(line, '{') - count(line, '}');
            if (depthBefore != 1) {
                continue;
            }
            Matcher m = MEMBER.matcher(line);
            if (!m.find()) {
                continue;
            }
            String modifier = m.group(1);
            String kind = modifier == null ? "abstract" : modifier;
            if (kind.equals("protected")) {
                kind = "abstract"; // 接口里带 protected 的成员同样属于强制面
            }
            out.add(kind + " " + m.group(2));
        }
    }

    private static int count(String text, char c) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
