package com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.persistence;

import com.github.magif1712.smarter_touhou_maids.architecture.ArchitectureFacts;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link PathTokenLogic} 的 golden 样本测试：锁死"沿递归链拼路径 token"的行为。
 * <p>
 * <b>本测试 2026-xx 重修过一次</b>：旧版手工图镜像的是<b>重构前</b>的旧结构
 * （{@code stm:} 前缀、{@code ai_smarter}/{@code process_smarter} 两个 AI/PROCESS 插槽、
 * {@code original_mapper → bnn_mapper_nn}、默认 {@code standard_bnn}），与生产概念树三处不符，
 * 断言的是"另一个世界的 token"——即<b>假绿安全网</b>。现按生产实况重建：
 * <ul>
 *   <li>id 由 {@link ArchitectureFacts} 提供（id 一旦改名，本测试随清单一起失败，不会静默漂移）；</li>
 *   <li>链路 = 生产默认链：{@code agent→ai→process→mapper→original_mapper_nn}；</li>
 *   <li>默认 token = {@link ArchitectureFacts#DEFAULT_PATH_TOKEN}。</li>
 * </ul>
 * <p>
 * <b>手工图 vs 真实图</b>：真实图需实例化概念树，而 {@code NodeKey} 依赖 Minecraft 的
 * {@code ResourceLocation}（纯 JUnit 环境不可用）——故本测试用"镜像生产的手工图"，
 * 由 {@code ThreeWayMirrorTest}/{@code FrozenIdsTest} 保证手工图与生产的 id 不脱节
 * （W4 解耦 MC 依赖后再升级为从 {@code RegistrySnapshot} 导出真实图）。
 * <p>
 * <b>字形</b>（设计原则5）：辅助方法 {@code void} + DPS 出参或值语义纯函数（G7 例外）；
 * {@code @Test} 无参 void 签名属框架豁免（I6）。
 */
class PathTokenGoldenTest {

    /** 手工层：id → entry(path, subLayerId)。 */
    private record TestLayer(String defaultEntryId, Map<String, String[]> entries)
            implements PathTokenLogic.Layer {
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

    /** 全限定 id（{@code smarter_touhou_maids:<path>}）—— 与生产 {@code NodeKey.id()} 同形。 */
    private static String id(String path) {
        return ArchitectureFacts.MOD_ID + ":" + path;
    }

    /**
     * 镜像生产的图（含 sensor/effector 层：它们不在主轴链上，证明 token 只沿"第一个非
     * sensor/effector 的 child"下钻，与 {@code SmarterLayerWalker.layerViewOf} 一致）。
     */
    private static Map<String, PathTokenLogic.Layer> productionGraph() {
        Map<String, PathTokenLogic.Layer> g = new HashMap<>();

        g.put(id("agent"), new TestLayer(id("smarter"), Map.of(
                id("smarter"), new String[]{"smarter", id("ai")})));
        g.put(id("ai"), new TestLayer(id("process_ai"), Map.of(
                id("process_ai"), new String[]{"process_ai", id("process")})));
        g.put(id("process"), new TestLayer(id("urana"), Map.of(
                id("urana"), new String[]{"urana", id("mapper")})));
        g.put(id("mapper"), new TestLayer(id("original_mapper"), Map.of(
                id("original_mapper"), new String[]{"original_mapper", id("original_mapper_nn")},
                id("bnn_mapper"), new String[]{"bnn_mapper", id("bnn_mapper_nn")},
                id("cnn_active_mapper"), new String[]{"cnn_active_mapper", id("cnn_active_mapper_nn")})));
        g.put(id("original_mapper_nn"), new TestLayer(id("cnn"), Map.of(
                id("cnn"), new String[]{"cnn", null})));
        g.put(id("bnn_mapper_nn"), new TestLayer(id("bnn"), Map.of(
                id("bnn"), new String[]{"bnn", null},
                id("standard_bnn"), new String[]{"standard_bnn", null})));
        g.put(id("cnn_active_mapper_nn"), new TestLayer(id("cnn_active"), Map.of(
                id("cnn_active"), new String[]{"cnn_active", null})));

        // 非主轴层：图上存在，但不参与 token（layerViewOf 跳过 sensor/effector）
        g.put(id("sensor"), new TestLayer(id("on_demand_possession_sensor"), Map.of(
                id("possession_sensor"), new String[]{"possession_sensor", null},
                id("on_demand_possession_sensor"), new String[]{"on_demand_possession_sensor", null})));
        g.put(id("effector"), new TestLayer(id("bionic_muscle_effector"), Map.of(
                id("bionic_muscle_effector"), new String[]{"bionic_muscle_effector", null})));
        return g;
    }

    /** 由手工图 + 选择计算 token。 */
    private static String token(Map<String, PathTokenLogic.Layer> graph, Map<String, String> selection) {
        Function<String, PathTokenLogic.Layer> layerById = graph::get;
        Function<String, String> currentByLayer = selection::get;
        return PathTokenLogic.pathToken(id("agent"), layerById, currentByLayer);
    }

    // ==================== 默认链（真实 token） ====================
    //
    // 语义对齐（与 SmarterLayerWalker.layerViewOf 一致）：
    //   Layer.defaultEntryId() → 全限定 branch id（node.defaultBranch(/* <- */ ).toString()）
    //   Layer.entryPath(id)    → 该 id 的 path 分量（branch.id().getPath()）
    //   Layer.subLayerId(id)   → 下一层插槽的全限定 id
    //   调用点传入的 currentByLayer 值也是全限定 branch id（Selection 里存的是 ResourceLocation）

    @Test
    void 新版代理全默认_回退到各层默认entry() {
        assertEquals(ArchitectureFacts.DEFAULT_PATH_TOKEN, token(productionGraph(), Map.of()),
                "默认链 token 是旧存档的持久化目录 key——它的变化必须是有意的迁移，不能是重构副作用");
    }

    @Test
    void mapper切到bnn_mapper_nn自动跟随bnn() {
        Map<String, String> sel = Map.of(id("mapper"), id("bnn_mapper"));
        assertEquals(ArchitectureFacts.BNN_MAPPER_PATH_TOKEN, token(productionGraph(), sel));
    }

    @Test
    void mapper切到cnn_active_mapper_nn自动跟随cnn_active() {
        Map<String, String> sel = Map.of(id("mapper"), id("cnn_active_mapper"));
        assertEquals(ArchitectureFacts.CNN_ACTIVE_PATH_TOKEN, token(productionGraph(), sel));
    }

    // W3b：旧版链（process=urana_original → nn_legacy）随旧版包下线而取消，
    // 相关 token 用例一并删除；旧存档的选择按 Resolver 规则回退到默认分支（urana）。

    @Test
    void 非主轴层_sensor与effector的选择不入token() {
        Map<String, String> sel = Map.of(
                id("sensor"), id("possession_sensor"),
                id("effector"), id("bionic_muscle_effector"));
        assertEquals(ArchitectureFacts.DEFAULT_PATH_TOKEN, token(productionGraph(), sel),
                "token 只沿'第一个非 sensor/effector 的 child'下钻（SmarterLayerWalker.layerViewOf）");
    }

    // ==================== 结构语义（与旧版一致，不得退化） ====================

    @Test
    void 未知entry_该层及以下不贡献分量() {
        Map<String, String> sel = Map.of(id("process"), "ghost");
        assertEquals("smarter__process_ai", token(productionGraph(), sel),
                "未注册的 entry 使该层及更下层终止（PathTokenLogic.appendChain）");
    }

    @Test
    void 层未注册_终止() {
        Map<String, PathTokenLogic.Layer> g = productionGraph();
        g.remove(id("mapper"));
        assertEquals("smarter__process_ai__urana", token(g, Map.of()));
    }

    @Test
    void 选中值在无默认层_该层不贡献分量() {
        Map<String, PathTokenLogic.Layer> g = productionGraph();
        g.put(id("mapper"), new TestLayer(null, Map.of(
                id("original_mapper"), new String[]{"original_mapper", id("original_mapper_nn")})));
        assertEquals("smarter__process_ai__urana", token(g, Map.of()));
    }

    @Test
    void 显式选中已注册entry_按选中值拼装() {
        Map<String, String> sel = Map.of(id("agent"), id("smarter"));
        assertEquals(ArchitectureFacts.DEFAULT_PATH_TOKEN, token(productionGraph(), sel));
    }
}
