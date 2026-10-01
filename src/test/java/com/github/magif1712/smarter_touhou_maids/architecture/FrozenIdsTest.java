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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 冻结清单校验：生产源码里的 id 字面量 / lang key 必须与 {@link ArchitectureFacts} 完全一致。
 * <p>
 * <b>为什么用"恰好相等"而不是"包含"</b>（真善美第2条）：{@code ⊆} 只能发现"少了"，发现不了"多了"。
 * 而"多了"正是熵增的来源——新插槽/新分支悄悄出现而不登记，正是上一轮查出的病根之一。
 * 故本测试对 id、lang key 都要求<b>集合相等</b>：多一个或少一个都红，逼登记。
 * <p>
 * <b>字形</b>（设计原则5）：辅助方法一律 {@code void} + DPS 出参（入参在左、出参在右，
 * 方向标记塞在参数表内）；{@code @Test} 方法的无参 void 签名是 JUnit 框架强制，属豁免（I6）。
 */
class FrozenIdsTest {

    /** 生产源码根（Gradle 测试工作目录 = 项目根）。 */
    private static final Path MAIN_JAVA = Paths.get("src/main/java");
    /** 中文语言文件（key 的权威来源）。 */
    private static final Path LANG_ZH =
            Paths.get("src/main/resources/assets/smarter_touhou_maids/lang/zh_cn.json");

    /** {@code new ResourceLocation(<MOD_ID 表达式>, "字面量")}。 */
    private static final Pattern RESOURCE_LOCATION_LITERAL = Pattern.compile(
            "new\\s+ResourceLocation\\(\\s*(?:SmarterTouhouMaids\\.MOD_ID|modId)\\s*,\\s*\"([^\"]+)\"\\s*\\)");
    /** {@code static final String <SOMETHING>_ID = "字面量";}。 */
    private static final Pattern ID_CONSTANT = Pattern.compile(
            "static\\s+final\\s+String\\s+\\w*_ID\\s*=\\s*\"([^\"]+)\"");
    /** {@code "mode.<modId>.<...>"} 形式的 lang key。 */
    private static final Pattern LANG_MODE_KEY = Pattern.compile("\"(mode\\.[^\"]+)\"");

    @Test
    void id字面量集合必须恰好等于冻结清单() {
        List<String> literals = new ArrayList<>();
        collectSourceLiterals(MAIN_JAVA /*->*/, literals);

        Set<String> expected = new TreeSet<>(ArchitectureFacts.FROZEN_ID_LITERALS);
        expected.addAll(ArchitectureFacts.PARAM_STORE_KEYS);

        assertEquals(expected, new TreeSet<>(literals),
                "id 字面量集合漂移：插槽/分支/协议 id 的增删改都必须在 ArchitectureFacts 登记"
                        + "（改名会破坏旧存档的持久化目录 token）");
    }

    @Test
    void 冻结清单自身必须自洽() {
        Set<String> nodeKeys = new TreeSet<>(ArchitectureFacts.BRANCHES_BY_NODE.keySet());
        assertEquals(new TreeSet<>(ArchitectureFacts.NODE_PATHS), nodeKeys,
                "BRANCHES_BY_NODE 的键集合必须恰好等于 NODE_PATHS");
        assertEquals(nodeKeys, new TreeSet<>(ArchitectureFacts.DEFAULT_BRANCH_BY_NODE.keySet()),
                "DEFAULT_BRANCH_BY_NODE 必须覆盖全部插槽");

        List<String> badDefaults = new ArrayList<>();
        ArchitectureFacts.DEFAULT_BRANCH_BY_NODE.forEach((node, branch) -> {
            if (!ArchitectureFacts.BRANCHES_BY_NODE.getOrDefault(node, List.of()).contains(branch)) {
                badDefaults.add(node + " -> " + branch);
            }
        });
        assertTrue(badDefaults.isEmpty(), "默认分支必须是自己插槽下的分支，非法项：" + badDefaults);

        List<String> undone = new ArrayList<>();
        for (String node : ArchitectureFacts.NODE_PATHS) {
            if (!ArchitectureFacts.FROZEN_ID_LITERALS.contains(node)) {
                undone.add(node);
            }
        }
        for (List<String> branches : ArchitectureFacts.BRANCHES_BY_NODE.values()) {
            for (String branch : branches) {
                if (!ArchitectureFacts.FROZEN_ID_LITERALS.contains(branch)) {
                    undone.add(branch);
                }
            }
        }
        assertTrue(undone.isEmpty(),
                "插槽/分支 id 必须都在 FROZEN_ID_LITERALS 中（源码扫描的期望集合）：" + undone);
    }

    @Test
    void lang的mode键集合必须恰好等于冻结清单() {
        List<String> keys = new ArrayList<>();
        collectLangModeKeys(LANG_ZH /*->*/, keys);
        assertEquals(new TreeSet<>(ArchitectureFacts.LANG_MODE_KEYS), new TreeSet<>(keys),
                "lang 的 mode.* key 漂移：新增/删除 key 都必须在 ArchitectureFacts 登记");
    }

    @Test
    void 已登记的孤儿lang键在删除前必须仍然存在() {
        List<String> keys = new ArrayList<>();
        collectLangModeKeys(LANG_ZH /*->*/, keys);
        for (String orphan : ArchitectureFacts.ORPHAN_LANG_KEYS) {
            assertTrue(keys.contains(orphan),
                    "孤儿 key 已不在 lang 文件里：" + orphan
                            + " —— 说明它已被删除，请同步缩小 ORPHAN_LANG_KEYS（真善美第2条：清单要收敛）");
        }
    }

    @Test
    void 协议标识与参数键必须出现在生产源码中() {
        List<String> all = new ArrayList<>();
        collectSourceLiterals(MAIN_JAVA /*->*/, all);
        Set<String> literals = new TreeSet<>(all);

        for (String protocol : ArchitectureFacts.PROTOCOL_IDS) {
            assertTrue(literals.contains(protocol), "协议标识丢失：" + protocol);
        }
        for (String paramKey : ArchitectureFacts.PARAM_STORE_KEYS) {
            assertTrue(literals.contains(paramKey), "ParamStore 键丢失：" + paramKey);
        }
    }

    // ==================== 扫描（DPS：入参 + /*->*/ 出参） ====================

    /** 扫描生产源码，把 id 字面量收进 out（排除模组 id 自身）。 */
    private static void collectSourceLiterals(Path root /*->*/, List<String> out) {
        List<Path> files = new ArrayList<>();
        collectJavaFiles(root /*->*/, files);
        for (Path file : files) {
            for (String line : readLines(file)) {
                Matcher location = RESOURCE_LOCATION_LITERAL.matcher(line);
                while (location.find()) {
                    out.add(location.group(1));
                }
                Matcher constant = ID_CONSTANT.matcher(line);
                while (constant.find()) {
                    String value = constant.group(1);
                    if (!ArchitectureFacts.MOD_ID.equals(value)) {
                        out.add(value);
                    }
                }
            }
        }
    }

    /** 从 lang 文件里收 {@code mode.*} key。 */
    private static void collectLangModeKeys(Path langFile /*->*/, List<String> out) {
        Matcher matcher = LANG_MODE_KEY.matcher(readWhole(langFile));
        while (matcher.find()) {
            out.add(matcher.group(1));
        }
    }

    /** 递归收集 {@code *.java}（按路径排序，让失败信息稳定可复现）。 */
    private static void collectJavaFiles(Path dir /*->*/, List<Path> out) {
        try (Stream<Path> walk = Files.walk(dir)) {
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

    private static String readWhole(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
