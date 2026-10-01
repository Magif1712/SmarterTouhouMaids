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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字形校验（设计原则5）：DPS 方向标记必须与"无返回值"契约一致。
 * <p>
 * <b>已实现的规则</b>（可判定、当前为真，故作强断言而非基线）：
 * <ol>
 *   <li><b>G1 定义点必须 void</b>：任何<b>方法声明</b>里出现方向标记（入参/出参分界标记，
 *       或"接收者为出参"的首部标记），其返回类型必须是 {@code void}——因为出参走参数表，
 *       返回值会形成第二个出口，读者会误判结果从哪来。</li>
 *   <li><b>G3 防倒退</b>：方向标记的出现次数不得低于
 *       {@link ArchitectureFacts#DIRECTION_MARK_BASELINE}。</li>
 * </ol>
 * <b>为何 G1 需要"跨行签名聚合"</b>：本项目的长签名大量换行，判定必须取
 * "从上一个语句边界（以分号/左花括号/右花括号结尾的行）到当前行"的整段文本，
 * 否则会漏掉上一行的 {@code void} 而误报。预演（PowerShell 同规则）已确认当前违规数为 0。
 * <p>
 * <b>豁免</b>：{@code @SubscribeEvent}（37 处）、mixin（2 个）、JUnit {@code @Test}、构造器
 * 都自带框架强制签名，不参与 G1；注释行、调用点、lambda 由 {@code looksLikeDeclaration} 过滤。
 * <p>
 * <b>已知陷阱（W10 记录，两次踩坑）</b>：文本级批量替换若<b>未跳过注释行</b>，会把 javadoc 里的
 * <b>代码示例</b>改成"带方向标记的调用形式"，而标记的收尾字符序列会<b>提前结束 javadoc 块</b>，
 * 造成编译失败（本次误伤 6 处：4 处 javadoc 示例 + 2 处无参 getter）。教训：任何涉及标记的批量
 * 替换都必须跳过注释行，并以编译验证兜底。
 * <p>
 * <b>字形</b>（设计原则5）：辅助方法 {@code void} + DPS 出参。
 */
class DpsShapeTest {

    /** 生产源码根。 */
    private static final Path MAIN_JAVA = Paths.get("src/main/java");
    /** 入参/出参分界标记——<b>两种写法都算</b>（项目里并存 {@code /*->*&#47;} 与 {@code /* -> *&#47;}，
     *  W12 修正：此前只统计前者，导致覆盖率被低估近一半）。 */
    private static final List<String> MARK_IN_VARIANTS = List.of("/*->*/", "/* -> */");
    /** 接收者为出参时的首部标记（同理收录紧凑写法）。 */
    private static final List<String> MARK_SELF_VARIANTS = List.of("/* <- */", "/*<-*/");

    @Test
    void 带方向标记的方法声明必须无返回值() {
        List<String> defects = new ArrayList<>();
        collectMarkedDeclarationsWithReturn(MAIN_JAVA /*->*/, defects);
        assertTrue(defects.isEmpty(),
                "以下方法声明带 DPS 方向标记却仍有返回值类型——出参已走参数表，返回值会形成两个出口"
                        + "（设计原则5/G1）。改为 void 并把结果放进出参参数：\n" + String.join("\n", defects));
    }

    @Test
    void 方向标记总数不得倒退() {
        int marks = countDirectionMarks(MAIN_JAVA);
        assertTrue(marks >= ArchitectureFacts.DIRECTION_MARK_BASELINE,
                "方向标记总数从基线 " + ArchitectureFacts.DIRECTION_MARK_BASELINE + " 降到 " + marks
                        + "：重构只应增加标记（把违规方法改成 DPS）；若确实删了代码，请下调 DIRECTION_MARK_BASELINE");
    }

    // ==================== 扫描（DPS：入参 + /*->*/ 出参） ====================

    /** 收集"带标记但不是 void"的方法声明（跨行签名聚合后判定）。 */
    private static void collectMarkedDeclarationsWithReturn(Path root /*->*/, List<String> out) {
        List<Path> files = new ArrayList<>();
        collectJavaFiles(root /*->*/, files);
        for (Path file : files) {
            List<String> lines = readLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isCommentLine(line)) {
                    continue;
                }
                if (!containsAny(line, MARK_IN_VARIANTS) && !containsAny(line, MARK_SELF_VARIANTS)) {
                    continue;
                }
                String signature = aggregatedSignature(lines, i);
                if (!looksLikeDeclaration(signature) || looksLikeConstructor(signature)) {
                    continue;
                }
                if (!signature.contains("void")) {
                    out.add(root.relativize(file) + ":" + (i + 1) + " :: " + signature.trim());
                }
            }
        }
    }

    /** 从上一个语句边界之后拼到当前行——得到（可能跨行的）完整签名。 */
    private static String aggregatedSignature(List<String> lines, int index) {
        int start = index;
        while (start > 0 && !isStatementBoundary(lines.get(start - 1))) {
            start--;
        }
        StringBuilder sb = new StringBuilder();
        for (int k = start; k <= index; k++) {
            sb.append(lines.get(k).trim()).append(' ');
        }
        return sb.toString();
    }

    private static boolean isStatementBoundary(String line) {
        String trimmed = line.trim();
        // 注释行也是边界：否则聚合签名会把 javadoc 拼进来，使 looksLikeDeclaration 判定失败而漏检
        //（W6 修复：TreeRegistry.freeze 这样"有返回值却带方向标记"的方法曾被静默放过）
        return trimmed.endsWith(";") || trimmed.endsWith("{") || trimmed.endsWith("}")
                || trimmed.isEmpty() || isCommentLine(line);
    }

    /** 形如方法声明（有修饰符 + 有参数表 + 非赋值），排除 lambda 与调用点。 */
    private static boolean looksLikeDeclaration(String signature) {
        String trimmed = signature.trim();
        if (!(trimmed.startsWith("public ") || trimmed.startsWith("private ")
                || trimmed.startsWith("protected ") || trimmed.startsWith("static ")
                || trimmed.startsWith("final ") || trimmed.startsWith("synchronized ")
                || trimmed.startsWith("abstract ") || trimmed.startsWith("default "))) {
            return false;
        }
        return trimmed.contains("(") && !trimmed.contains("=");
    }

    /** 构造器（修饰符 + 类名 + 参数表，无返回类型）不参与 G1——它本来就没有返回值。 */
    private static boolean looksLikeConstructor(String signature) {
        return signature.trim().matches("^(public|private|protected)\\s+[A-Z]\\w*\\s*\\(.*");
    }

    /** 统计方向标记的出现次数（一行多个也计多次）。 */
    private static int countDirectionMarks(Path root) {
        List<Path> files = new ArrayList<>();
        collectJavaFiles(root /*->*/, files);
        int total = 0;
        for (Path file : files) {
            for (String line : readLines(file)) {
                for (String variant : MARK_IN_VARIANTS) {
                    total += countOccurrences(line, variant);
                }
                for (String variant : MARK_SELF_VARIANTS) {
                    total += countOccurrences(line, variant);
                }
            }
        }
        return total;
    }

    /** 命中任一写法即算（用于"这行是否带方向标记"的判定）。 */
    private static boolean containsAny(String text, List<String> variants) {
        for (String variant : variants) {
            if (text.contains(variant)) {
                return true;
            }
        }
        return false;
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(token, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + token.length();
        }
    }

    private static boolean isCommentLine(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("*") || trimmed.startsWith("//")
                || trimmed.startsWith("/*") || trimmed.isEmpty();
    }

    private static void collectJavaFiles(Path root /*->*/, List<Path> out) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
