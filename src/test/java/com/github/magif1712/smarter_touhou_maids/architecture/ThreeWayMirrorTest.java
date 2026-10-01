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
 * 结构层校验（定理1(4)）：Java 包目录与 native 目录必须互相对得上，且不得留空包。
 * <p>
 * <b>依据</b>：装配图（{@code ConceptTree}）表达"谁包含谁、谁与谁同层"；文件夹目录是这个
 * 装配关系在磁盘上的投影。二者一致才算"实物与符号相符"（真善美第2条）。三件事：
 * <ol>
 *   <li><b>不留空包</b>：空包是"已迁移/已不存在的模块"的壳，读者会以为那里还有东西。</li>
 *   <li><b>native 目录必须能对应到 Java 包</b>：{@code *_jni.cpp} 所在目录向上剥离若干层后，
 *       必须能落在某个真实存在的 Java 包目录上。找不到 ⇒ 该 native 文件是"孤儿"（无人引用）。</li>
 *   <li><b>已达成镜像的模块必须两侧同路径</b>：含 JNI 绑定的模块，其 native 目录应与 Java
 *       包路径<b>逐层一致</b>——JNI 符号串只保证运行期能找到（见 {@link JniSymbolMirrorTest}），
 *       目录对齐保证人能一眼看出对应关系。</li>
 * </ol>
 * <b>注</b>：Python 伪代码文档（{@code Document/Python-style pseudocode document}）是历史遗留，
 * <b>不作为</b>本项目的对齐依据；权威是"设计原则 + 代码里的装配图"。
 * <p>
 * <b>字形</b>（设计原则5）：辅助方法 {@code void} + DPS 出参。
 */
class ThreeWayMirrorTest {

    /** Java 生产根（相对项目根）。 */
    private static final Path JAVA_BASE =
            Paths.get("src/main/java/com/github/magif1712/smarter_touhou_maids");
    /** native 源根（相对项目根）。 */
    private static final Path NATIVE_BASE = Paths.get("native/stm_ai/src");
    /** Java 测试根（与 {@link #JAVA_BASE} 同包前缀，供包名/空目录断言共用）。 */
    private static final Path TEST_BASE =
            Paths.get("src/test/java/com/github/magif1712/smarter_touhou_maids");
    /** jni.cpp 目录向上剥离的最大层数（interop → mapping/x → 叶子包 → ...）。 */
    private static final int MAX_ASCEND = 10;

    @Test
    void java侧不得有空目录() {
        List<String> empty = new ArrayList<>();
        collectEmptyDirs(JAVA_BASE /*->*/, empty);
        collectEmptyDirs(TEST_BASE /*->*/, empty);
        assertTrue(empty.isEmpty(),
                "Java 侧出现空目录（实物与符号不符，真善美第2条）——请删除这些空壳包：\n" + String.join("\n", empty));
    }

    /**
     * 每个 {@code .java} 的 {@code package} 声明必须与它所在的物理目录一致（生产与测试两侧）。
     * <p>
     * <b>为什么必须有这条</b>：{@code javac} 只看 {@code package} 声明、<b>不看</b>物理路径，
     * 所以"文件在旧目录、声明已改"这种半迁移状态<b>能编译通过、测试也全绿</b>——
     * 项目里已因此埋过两次雷（W8 的 {@code CnnActiveNeuralNetworkTest}、W20 又重犯一次）。
     * 目录是装配关系的投影，两者必须相符；此断言把这条不实在的纪律实在化。
     * <p>
     * <b>依据</b>：定理1(4)（目录 ⇄ 装配）+ 真善美第4条（把不实在的纪律转化为实在的断言）。
     */
    @Test
    void 每个java文件的包声明必须与所在目录一致() {
        List<String> mismatches = new ArrayList<>();
        collectPackageMismatch(Paths.get("src/main/java"), JAVA_BASE /*->*/, mismatches);
        collectPackageMismatch(Paths.get("src/test/java"), TEST_BASE /*->*/, mismatches);
        assertTrue(mismatches.isEmpty(),
                "package 声明与物理目录不一致（javac 不会报错，只能靠本断言兜住）——"
                        + "请把文件移到与声明相符的目录，或改声明：\n" + String.join("\n", mismatches));
    }

    @Test
    void native侧不得有空目录() {
        List<String> empty = new ArrayList<>();
        collectEmptyDirs(NATIVE_BASE /*->*/, empty);
        assertTrue(empty.isEmpty(),
                "native 侧出现空目录（实物与符号不符）——请删除这些空壳目录：\n" + String.join("\n", empty));
    }

    @Test
    void 含JNI绑定的native目录必须能对应到Java包() {
        List<Path> jniFiles = new ArrayList<>();
        collectFiles(NATIVE_BASE /*->*/, "_jni.cpp", jniFiles);

        List<String> orphans = new ArrayList<>();
        for (Path jni : jniFiles) {
            if (!hasJavaCounterpart(jni)) {
                orphans.add(NATIVE_BASE.relativize(jni).toString().replace('\\', '/'));
            }
        }
        assertTrue(orphans.isEmpty(),
                "以下 *_jni.cpp 向上剥离后找不到对应的 Java 包（孤儿 native 文件）：\n" + String.join("\n", orphans));
    }

    @Test
    void 已达成镜像的模块在两侧必须同路径存在() {
        List<String> missing = new ArrayList<>();
        for (String module : ArchitectureFacts.MIRRORED_MODULE_PATHS) {
            if (!Files.isDirectory(JAVA_BASE.resolve(module))) {
                missing.add("Java 侧缺: " + module);
            }
            if (!Files.isDirectory(NATIVE_BASE.resolve(module))) {
                missing.add("native 侧缺: " + module);
            }
        }
        assertTrue(missing.isEmpty(),
                "镜像对缺位——两侧路径必须逐层一致（定理1(4)）：\n" + String.join("\n", missing));
    }

    @Test
    void native源文件的绝对路径不得超过平台上限() {
        List<Path> files = new ArrayList<>();
        collectAllFiles(NATIVE_BASE /*->*/, files);

        List<String> tooLong = new ArrayList<>();
        for (Path file : files) {
            int length = file.toAbsolutePath().toString().length();
            if (length > ArchitectureFacts.NATIVE_ABS_PATH_LIMIT) {
                tooLong.add(length + " 字符 :: " + file.toString().replace('\\', '/'));
            }
        }
        assertTrue(tooLong.isEmpty(),
                "native 源文件绝对路径超过平台上限 " + ArchitectureFacts.NATIVE_ABS_PATH_LIMIT
                        + "（Windows MAX_PATH 260，nvcc 的中间产物路径更长）——"
                        + "挪目录时必须先算长度，否则 cmakeBuild 会以 nvcc fatal 失败：\n"
                        + String.join("\n", tooLong));
    }

    // ==================== 扫描（DPS：入参 + /*->*/ 出参） ====================

    /**
     * 收集 {@code srcRoot} 下 package 声明与物理目录不符的 {@code .java}，写入 {@code out}。
     *
     * @param srcRoot 包的根目录（如 {@code src/main/java}）
     * @param base    要扫描的包前缀目录（如 {@code …/smarter_touhou_maids}）
     */
    private static void collectPackageMismatch(Path srcRoot, Path base /*->*/, List<String> out) {
        List<Path> files = new ArrayList<>();
        collectFiles(base /*->*/, ".java", files);
        for (Path file : files) {
            String declared = declaredPackage(file);
            if (declared == null) {
                continue; // 默认包：不在此断言范围内
            }
            String expected = srcRoot.relativize(file.getParent()).toString()
                    .replace('\\', '.').replace('/', '.');
            if (!declared.equals(expected)) {
                out.add(srcRoot.relativize(file).toString().replace('\\', '/')
                        + "\n      声明: " + declared
                        + "\n      目录: " + expected);
            }
        }
    }

    /** 读该文件声明的包名；无 {@code package} 声明（默认包）返回 null。 */
    private static String declaredPackage(Path javaFile) {
        try (Stream<String> lines = Files.lines(javaFile)) {
            return lines.limit(50)
                    .map(String::trim)
                    .filter(line -> line.startsWith("package "))
                    .map(line -> line.substring("package ".length()).replace(";", "").trim())
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 收集 {@code base} 下所有<b>空目录</b>（子树内不含任何普通文件），路径相对 base、以 {@code /} 分隔。
     * <p>判定与 {@code Get-ChildItem -Recurse -File} 的计数一致：包被搬空只留壳时，壳本身与其父壳都计为空。
     */
    private static void collectEmptyDirs(Path base /*->*/, List<String> out) {
        List<Path> dirs = new ArrayList<>();
        collectDirs(base /*->*/, dirs);
        for (Path dir : dirs) {
            if (!containsAnyFile(dir)) {
                out.add(base.relativize(dir).toString().replace('\\', '/'));
            }
        }
    }

    private static void collectDirs(Path base /*->*/, List<Path> out) {
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isDirectory).sorted().forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean containsAnyFile(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.anyMatch(Files::isRegularFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 该 {@code *_jni.cpp} 向上剥离后能否落在某个真实存在的 Java 包目录上。 */
    private static boolean hasJavaCounterpart(Path jniFile) {
        Path dir = jniFile.getParent();
        for (int ascend = 0; ascend < MAX_ASCEND && dir != null; ascend++) {
            if (!dir.startsWith(NATIVE_BASE)) {
                return false;
            }
            Path javaEquivalent = JAVA_BASE.resolve(NATIVE_BASE.relativize(dir));
            if (Files.isDirectory(javaEquivalent)) {
                return true;
            }
            dir = dir.getParent();
        }
        return false;
    }

    private static void collectAllFiles(Path base /*->*/, List<Path> out) {
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile).sorted().forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void collectFiles(Path base /*->*/, String suffix, List<Path> out) {
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(suffix))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
