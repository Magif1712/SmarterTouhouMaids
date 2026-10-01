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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 顶层形态校验（定理1(4) + 原则4）：把"面 / 模式 / 支持物"的区分从**文档**（不实在）变成**清单**（实在）。
 * <p>
 * <b>依据（W39 版设计原则）</b>：(4) 现明确"**对于可设计系统为代码系统的情况**…在文件夹目录中平级放置
 * S 和 T 的包…**对于其它类型的可设计系统也是同理**"⟹ 判据是通用两问：①模式（S、T）是否平级放置？
 * ②组合关系（A 包含 S、T）是否由**显式装配图**表达？本类锁"顶层形态"这一面：
 * <ol>
 *   <li><b>顶层只允许"面"</b>：面目录（{@link #SURFACES}）+ 已登记的散支持包
 *       （{@link #SUPPORT_PACKAGES}，收进面里之后必须删行——双向锁）。
 *       <b>模式包不再出现在顶层</b>：按概念分类后它们住在 {@code modes/<概念>/<系统>}
 *       （清单的单一数据源仍是 {@link SiblingPlacementTest#SYSTEM_PACKAGES}）。</li>
 *   <li><b>面内与概念目录内不得挂模式</b>：面 = 不挂在装配图上的同类支持物；概念目录的<b>直接文件</b>
 *       是该概念的<b>要素（内涵）</b>——故两者都不得出现模式物（{@code *Registration}/{@code *Modes}/
 *       {@code *Defaults} 文件，或 {@code new Branch<>} 装配调用）。</li>
 *   <li><b>另一类代码系统（GUI 面板集）同样适用</b>：{@code panels/} 下必须平级（不得嵌套），
 *       且面板集合必须与 {@code DefaultPanels} 的注册集合<b>完全一致</b>（装配表达显式、双向锁）。</li>
 * </ol>
 * <b>W46 记录</b>：{@code contract} 与 {@code nn} 两个顶层名消失——契约的 7 个层包成为 {@code modes/}
 * 的 7 个<b>概念</b>目录（内涵），nn 的共享机制（原 {@code nn/{bnn,cnn}}）成为 nn 概念的<b>内涵分类</b>。
 * 故"面内不得挂模式"的判定由**面名**升级为**路径制**（{@link #MODE_FREE_ROOTS} / {@link #CONCEPT_DIRS}）：
 * 否则概念目录下潜后，这层保护会静默丢失。
 * <p>
 * <b>W48 记录（面收敛为两个）</b>：{@code harness} 改名 {@code infrastructure}，原 {@code service} 面并入同一面
 * （二者互相依赖、都不挂装配图 ⟹ 按判据本就是同一个面），故 {@link #SURFACES} 收敛为
 * {@code {infrastructure, modes}}：<b>模式世界（概念 + 系统）</b>与<b>骨架（让它们存在并运转的一切支撑物）</b>。

 * <b>字形</b>（设计原则5）：辅助方法 {@code void} + DPS 出参。
 */
class TopLevelShapeTest {

    /** smarter 域根。 */
    private static final Path SMARTER = Paths.get(
            "src/main/java/com/github/magif1712/smarter_touhou_maids/features/smarter");
    /** GUI 面根（另一类代码系统）。 */
    private static final Path GUI_BASE = Paths.get(
            "src/main/java/com/github/magif1712/smarter_touhou_maids/features/ui/config_gui/standard_config_gui");
    /** 面板集目录（模式必须平级）。 */
    private static final Path PANELS = GUI_BASE.resolve("panels");
    /** 面板集的装配图（显式装配表达）。 */
    private static final Path DEFAULT_PANELS = GUI_BASE.resolve("DefaultPanels.java");

    /** 面目录：不挂在装配图上的同类支持物（骨架=基础设施+服务、模式）。 */
    private static final List<String> SURFACES = List.of("infrastructure", "modes");

    /** 已登记的散支持包（尚未成面）。<b>收进面里之后必须从本清单删行</b>（双向锁，避免腐烂成永久 TODO）。 */
    private static final List<String> SUPPORT_PACKAGES = List.of(); // W41 收服务包、W48 服务并入骨架面（账本保持清零）

    /** 模式物的文件名特征（模式自己声明装配：Registration / Modes / Defaults）。 */
    private static final Pattern MODE_FILE = Pattern.compile(".*(Registration|Modes|Defaults)\\.java");

    /**
     * 需<b>递归</b>检查"不得含模式物、不得构造 Branch"的路径（相对 {@link #SMARTER}）。
     * <p>
     * 骨架面全域在列（W48 起它同时含原 {@code service} 面：执行 / 网络 / 附身 / 状态）；
     * {@code modes/nn/{bnn,cnn}} 在列——它们是 nn 概念的<b>共享机制（内涵）</b>，由同一条纪律保护
     * （它们同样不挂装配图）。
     * <p>
     * <b>为什么不再分"模式物清单"与"装配清单"两张表</b>：W41 曾把 {@code service/} 单列为只查
     * "不得构造 Branch"（理由是它的网络通道有自己的注册物）。W48 复核发现那些通道注册物住在 mod 根的
     * {@code network/NetworkHandler}，<b>不在本面内</b>，故两张表已可合一；若将来面内真出现通道级注册物，
     * 必须在此<b>显式回答</b>（说明它为何不是模式物），而不是静默豁免。
     */
    private static final List<String> MODE_FREE_ROOTS = List.of(
            "infrastructure", "modes/nn/bnn", "modes/nn/cnn");

    /**
     * 概念目录（相对 {@link #SMARTER}）：只检查其<b>直接文件</b>。
     * <p>
     * <b>为什么只查直接文件</b>：概念目录 = 该概念的"层"本身，直接文件是它的<b>要素（内涵）</b>
     * （插槽 id / 槽类型接口 / 工厂契约 / X-Y 载体）；其子目录是<b>外延</b>（候选系统），
     * 那里理应有 {@code *Registration}/{@code *Modes} 与 {@code new Branch<>}。
     * <p>
     * 依据：定理1(4) 手段③（按概念分类）+ 定理1(2)（内涵与外延不多不少地各居其位）。
     */
    private static final List<String> CONCEPT_DIRS = List.of(
            "modes/agent", "modes/ai", "modes/effector", "modes/mapper",
            "modes/nn", "modes/process", "modes/sensor");

    /** 装配调用的特征（构造 Branch）。 */
    private static final String BRANCH_CONSTRUCTION = "new Branch<";

    /** DefaultPanels 里的注册表达式。 */
    private static final Pattern PANEL_REGISTRATION = Pattern.compile("new\\s+([A-Z]\\w*Panel)\\s*\\(");

    @Test
    void 顶层只能是面_模式包_或已登记的支持包() {
        List<String> actual = new ArrayList<>();
        collectDirNames(SMARTER /*->*/, actual);

        List<String> allowed = new ArrayList<>(SURFACES);
        allowed.addAll(SUPPORT_PACKAGES);

        List<String> problems = new ArrayList<>();
        for (String name : actual) {
            if (!allowed.contains(name)) {
                problems.add(name + "：未登记的顶层目录——请先归类（面 / 模式包 / 支持包）再登记");
            }
        }
        for (String name : allowed) {
            if (!actual.contains(name)) {
                problems.add(name + "：已登记但目录不存在——请同步清单");
            }
        }
        assertTrue(problems.isEmpty(),
                "顶层形态不符（定理1(4)：模式平级、组合交给装配图；原则4：面要实在）：\n"
                        + String.join("\n", problems));
    }

    @Test
    void 面与内涵目录内不得挂模式() {
        List<String> problems = new ArrayList<>();
        for (String root : MODE_FREE_ROOTS) {
            List<Path> files = new ArrayList<>();
            collectJavaFiles(SMARTER.resolve(root) /*->*/, files);
            for (Path file : files) {
                String relative = SMARTER.relativize(file).toString().replace('\\', '/');
                if (MODE_FILE.matcher(file.getFileName().toString()).matches()) {
                    problems.add(relative + "：面内出现了模式物（*Registration/*Modes/*Defaults）——模式应挂装配图");
                }
                for (String line : readLines(file)) {
                    if (line.contains(BRANCH_CONSTRUCTION)) {
                        problems.add(relative + "：面内出现了装配调用（" + BRANCH_CONSTRUCTION
                                + "）——装配属装配图");
                        break;
                    }
                }
            }
        }
        for (String concept : CONCEPT_DIRS) {
            List<Path> files = new ArrayList<>();
            collectDirectJavaFiles(SMARTER.resolve(concept) /*->*/, files);
            for (Path file : files) {
                String relative = SMARTER.relativize(file).toString().replace('\\', '/');
                if (MODE_FILE.matcher(file.getFileName().toString()).matches()) {
                    problems.add(relative + "：概念目录的直接文件是要素（内涵），不得是模式物");
                }
                for (String line : readLines(file)) {
                    if (line.contains(BRANCH_CONSTRUCTION)) {
                        problems.add(relative + "：概念目录（内涵）不得替装配图做装配（"
                                + BRANCH_CONSTRUCTION + "）");
                        break;
                    }
                }
            }
        }

        assertTrue(problems.isEmpty(),
                "面与概念目录是不挂装配图的支持物/内涵，不得混入模式：\n"
                        + String.join("\n", problems));
    }

    @Test
    void GUI面板必须平级且与注册清单一致() {
        List<String> problems = new ArrayList<>();

        List<String> subDirs = new ArrayList<>();
        collectSubDirNames(PANELS /*->*/, subDirs);
        for (String dir : subDirs) {
            problems.add("panels/" + dir + "：面板集必须平级（(4)：模式不得用嵌套表达组合）");
        }

        TreeSet<String> declared = new TreeSet<>();
        List<Path> panelFiles = new ArrayList<>();
        collectJavaFiles(PANELS /*->*/, panelFiles);
        for (Path file : panelFiles) {
            declared.add(file.getFileName().toString().replace(".java", ""));
        }

        TreeSet<String> registered = new TreeSet<>();
        for (String line : readLines(DEFAULT_PANELS)) {
            Matcher m = PANEL_REGISTRATION.matcher(line);
            while (m.find()) {
                registered.add(m.group(1));
            }
        }
        if (!declared.equals(registered)) {
            problems.add("panels/ 的面板集合与 DefaultPanels 的注册集合不一致——\n"
                    + "      目录内: " + declared + "\n      注册表: " + registered);
        }
        assertTrue(problems.isEmpty(),
                "GUI 面板集不符（同理于定理1(4)：面板是模式，须平级并由装配表达显式登记）：\n"
                        + String.join("\n", problems));
    }

    /** 收集 {@code base} 的直接子目录名。 */
    private static void collectDirNames(Path base /*->*/, List<String> out) {
        try (Stream<Path> list = Files.list(base)) {
            list.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 收集 {@code base} 下更深的子目录（相对路径；用于"平级"校验）。 */
    private static void collectSubDirNames(Path base /*->*/, List<String> out) {
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isDirectory)
                    .filter(path -> !path.equals(base))
                    .map(path -> base.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 收集 {@code base} 下的<b>直接</b> {@code .java}（不递归；概念目录的要素文件检查用）。 */
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

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
