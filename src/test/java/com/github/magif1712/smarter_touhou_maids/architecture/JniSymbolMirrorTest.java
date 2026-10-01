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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * JNI 符号镜像校验：Java 侧 {@code native} 方法 ↔ native 侧 {@code JNI_METHOD(pkg, cls, method)}。
 * <p>
 * <b>为什么必须用测试锁住</b>（I3 地雷）：JNI 符号名 = Java 类的<b>完整包路径</b>的转义
 * （{@code .} → {@code _}、{@code _} → {@code _1}），且这个串是**手工写在 native 侧**的
 * （见 {@code core/interop/jni_helper.h} 的 {@code JNI_METHOD} 宏 + 各 {@code *_jni.cpp}）。
 * 因此"移动含 native 方法的类或改包名"时，编译器不会报错，只在运行期抛
 * {@code UnsatisfiedLinkError}——这是本项目最隐蔽的破坏方式。
 * <p>
 * <b>上一轮已证实的病灶</b>：native 的符号串跟上了 Java 的新包路径
 * （{@code ..._fittable_1mapper_cnn_1active_1mapper_nn_cnn_1active}），但 native 的<b>物理目录</b>
 * 仍留在旧位（{@code urana/nn/cnn_active/mapping/inference/interop}）——
 * 即"符号对了、目录没跟上"。本测试锁符号侧；目录侧由 {@link ThreeWayMirrorTest} 记录。
 * <p>
 * <b>字形</b>（设计原则5）：辅助方法 {@code void} + DPS 出参；{@code @Test} 无参 void 属框架豁免。
 */
class JniSymbolMirrorTest {

    /** Java 包根（JNI 前缀里被宏吃掉的部分）。 */
    private static final String JAVA_PKG_ROOT = "com.github.magif1712.smarter_touhou_maids";
    /** 生产源码根。 */
    private static final Path MAIN_JAVA = Paths.get("src/main/java");
    /** native 源码根。 */
    private static final Path NATIVE_SRC = Paths.get("native/stm_ai/src");

    private static final Pattern PACKAGE_DECL = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
    /** {@code [static] native <type> <method>(}。 */
    private static final Pattern NATIVE_METHOD = Pattern.compile("\\bnative\\s+[\\w\\[\\]<>.]*\\s+(\\w+)\\s*\\(");
    /** {@code JNI_METHOD(pkg, Class, method)}。 */
    private static final Pattern JNI_METHOD_CALL = Pattern.compile(
            "JNI_METHOD\\(\\s*(\\w+)\\s*,\\s*(\\w+)\\s*,\\s*(\\w+)\\s*\\)");

    @Test
    void java侧native方法与native侧JNI实现必须一一对应() {
        Set<String> javaSide = new TreeSet<>();
        collectJavaNativeMethods(MAIN_JAVA /*->*/, javaSide);
        Set<String> nativeSide = new TreeSet<>();
        collectJniSymbols(NATIVE_SRC /*->*/, nativeSide);

        assertFalse(javaSide.isEmpty(), "未扫到任何 Java native 方法——扫描规则或目录已失效");
        assertFalse(nativeSide.isEmpty(), "未扫到任何 JNI_METHOD 实现——扫描规则或目录已失效");
        assertEquals(javaSide, nativeSide,
                "Java 侧 native 方法与 native 侧 JNI_METHOD 实现不一致：含 native 的类移包/改名必须四联动"
                        + "（Java 源 + *_jni.cpp 的 JNI_METHOD 串 + CMakeLists.txt 路径 + #include 路径）");
    }

    @Test
    void native规模冻结基线() {
        Set<String> javaSide = new TreeSet<>();
        collectJavaNativeMethods(MAIN_JAVA /*->*/, javaSide);
        long classes = javaSide.stream().map(entry -> entry.substring(0, entry.indexOf('#'))).distinct().count();
        assertEquals(ArchitectureFacts.NATIVE_METHOD_BASELINE, javaSide.size(),
                "native 方法总数变化：增删 native 入口必须同步更新 NATIVE_METHOD_BASELINE 与 CMakeLists");
        assertEquals(ArchitectureFacts.NATIVE_CLASS_BASELINE, (int) classes,
                "含 native 方法的类数变化：请同步更新 NATIVE_CLASS_BASELINE");
    }

    // ==================== 扫描（DPS：入参 + /*->*/ 出参） ====================

    /** 收集 Java 侧 native 方法，元素形如 {@code <全限定类名>#<方法名>}。 */
    private static void collectJavaNativeMethods(Path root /*->*/, Set<String> out) {
        List<Path> files = new ArrayList<>();
        collectFiles(root /*->*/, ".java", files);
        for (Path file : files) {
            String fqn = readPackageAndClass(file);
            if (fqn == null) {
                continue;
            }
            for (String method : findAll(NATIVE_METHOD, stripCommentLines(file))) {
                out.add(fqn + "#" + method);
            }
        }
    }

    /** 收集 native 侧 JNI 实现，元素形如 {@code <全限定类名>#<方法名>}（转义串反解）。 */
    private static void collectJniSymbols(Path root /*->*/, Set<String> out) {
        List<Path> files = new ArrayList<>();
        collectFiles(root /*->*/, "_jni.cpp", files);
        for (Path file : files) {
            Matcher matcher = JNI_METHOD_CALL.matcher(stripCommentLines(file));
            while (matcher.find()) {
                String packagePath = decodeJniEncoding(matcher.group(1));
                String className = decodeJniEncoding(matcher.group(2));
                String method = matcher.group(3).replace("_1", "_");
                out.add(JAVA_PKG_ROOT + "." + packagePath + "." + className + "#" + method);
            }
        }
    }

    /**
     * JNI 名称编码的反解：{@code _1} 还原为下划线、{@code _} 还原为包分隔符。
     * <p>顺序敏感：先把 {@code _1} 换成一个不会出现的哨兵字符，否则 {@code _1} 会被拆成"分隔符 + 1"。
     */
    private static String decodeJniEncoding(String encoded) {
        return encoded.replace("_1", "\u0001").replace('_', '.').replace('\u0001', '_');
    }

    /** 读包声明 + 文件名（这些 {@code *Native} 类都是"一个文件一个类、类名同文件名"）。 */
    private static String readPackageAndClass(Path file) {
        for (String line : readLines(file)) {
            Matcher matcher = PACKAGE_DECL.matcher(line);
            if (matcher.find()) {
                String simpleName = file.getFileName().toString().replace(".java", "");
                return matcher.group(1) + "." + simpleName;
            }
        }
        return null;
    }

    /** 剔除注释行后拼成整段文本（注释里的示例代码不算声明；跨行调用也能匹配）。 */
    private static String stripCommentLines(Path file) {
        StringBuilder sb = new StringBuilder();
        for (String line : readLines(file)) {
            if (isCommentLine(line)) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static boolean isCommentLine(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.isEmpty();
    }

    private static List<String> findAll(Pattern pattern, String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    /** 递归收集指定后缀的文件（排序，让失败信息稳定）。 */
    private static void collectFiles(Path root /*->*/, String suffix, List<Path> out) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(suffix))
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
