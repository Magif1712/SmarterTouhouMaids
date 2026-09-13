package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link PathTokenLogic} 的 golden 样本测试：锁死"沿递归链拼路径 token"的行为，
 * 防止概念树重构（Node/Branch 化）过程中持久化目录键漂移导致旧持久化数据失配。
 * <p>
 * 手工图镜像生产结构：
 * <pre>
 * agent ──smarter──▶ ai_smarter ──process_ai──▶ process_smarter ──urana──▶ mapper ──original_mapper──▶ bnn_mapper_nn(叶子)
 * agent ──smarter_original──▶ ai ──process_ai──▶ process ──urana_original──▶ nn_legacy(叶子)
 * </pre>
 */
class PathTokenGoldenTest {

    /** 手工层：id → entry(path, subLayerId)。 */
    private record TestLayer(String defaultEntryId, Map<String, String[]> entries) implements PathTokenLogic.Layer {
        @Override
        public String entryPath(String entryId) {
            String[] e = entries.get(entryId);
            return e != null ? e[0] : null;
        }

        @Override
        public String subLayerId(String entryId) {
            String[] e = entries.get(entryId);
            return e != null ? e[1] : null;
        }
    }

    private static Map<String, PathTokenLogic.Layer> productionLikeGraph() {
        Map<String, PathTokenLogic.Layer> g = new HashMap<>();
        g.put("stm:agent", new TestLayer("smarter", Map.of(
                "smarter", new String[]{"smarter", "stm:ai_smarter"},
                "smarter_original", new String[]{"smarter_original", "stm:ai"})));
        g.put("stm:ai_smarter", new TestLayer("process_ai", Map.of(
                "process_ai", new String[]{"process_ai", "stm:process_smarter"})));
        g.put("stm:ai", new TestLayer("process_ai", Map.of(
                "process_ai", new String[]{"process_ai", "stm:process"})));
        g.put("stm:process_smarter", new TestLayer("urana", Map.of(
                "urana", new String[]{"urana", "stm:mapper"})));
        g.put("stm:process", new TestLayer("urana_original", Map.of(
                "urana_original", new String[]{"urana_original", "stm:nn_legacy"})));
        g.put("stm:mapper", new TestLayer("original_mapper", Map.of(
                "original_mapper", new String[]{"original_mapper", "stm:bnn_mapper_nn"})));
        g.put("stm:bnn_mapper_nn", new TestLayer("standard_bnn", Map.of(
                "standard_bnn", new String[]{"standard_bnn", null})));
        g.put("stm:nn_legacy", new TestLayer("standard_bnn", Map.of(
                "standard_bnn", new String[]{"standard_bnn", null})));
        return g;
    }

    private static String token(Map<String, PathTokenLogic.Layer> graph, Map<String, String> selection) {
        Function<String, PathTokenLogic.Layer> layerById = graph::get;
        Function<String, String> currentByLayer = selection::get;
        return PathTokenLogic.pathToken("stm:agent", layerById, currentByLayer);
    }

    @Test
    void 新版代理全默认_回退到各层默认entry() {
        assertEquals("smarter__process_ai__urana__original_mapper__standard_bnn",
                token(productionLikeGraph(), Map.of()));
    }

    @Test
    void 原初代理链_显式选中() {
        Map<String, String> sel = Map.of("stm:agent", "smarter_original");
        assertEquals("smarter_original__process_ai__urana_original__standard_bnn",
                token(productionLikeGraph(), sel));
    }

    @Test
    void 中层显式切换_下层回退默认() {
        Map<String, String> sel = Map.of(
                "stm:agent", "smarter",
                "stm:mapper", "original_mapper");
        assertEquals("smarter__process_ai__urana__original_mapper__standard_bnn",
                token(productionLikeGraph(), sel));
    }

    @Test
    void 未知entry_该层及以下不贡献分量() {
        Map<String, String> sel = Map.of("stm:process_smarter", "nonexistent");
        assertEquals("smarter__process_ai", token(productionLikeGraph(), sel));
    }

    @Test
    void 层未注册_终止() {
        Map<String, PathTokenLogic.Layer> g = productionLikeGraph();
        g.remove("stm:mapper");
        assertEquals("smarter__process_ai__urana", token(g, Map.of()));
    }

    @Test
    void 选中值在无默认层_该层不贡献分量() {
        Map<String, PathTokenLogic.Layer> g = productionLikeGraph();
        g.put("stm:mapper", new TestLayer(null, Map.of(
                "original_mapper", new String[]{"original_mapper", "stm:bnn_mapper_nn"})));
        assertEquals("smarter__process_ai__urana", token(g, Map.of()));
    }
}
