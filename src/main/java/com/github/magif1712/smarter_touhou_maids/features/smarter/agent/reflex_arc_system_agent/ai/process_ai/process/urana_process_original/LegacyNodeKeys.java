package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.NodeKey;
import net.minecraft.resources.ResourceLocation;

/**
 * 旧版 urana_process_original 层的插槽键常量（真善美第2条：每层只决定其下一层，不感知更下层）。
 * <p>
 * 本类定义旧版层<b>决定的直接下层</b>插槽：{@link #NN_LEGACY}（旧版神经网络插槽）。
 * 旧版层自身的插槽键（{@code smarter_touhou_maids:process}）由上层 AI 层的
 * {@code ProcessAiNodeKeys#PROCESS} 定义——旧版 urana_original Branch 挂进 PROCESS 插槽
 * （与新版 urana 同级并列），但其 children 指向独立的 NN_LEGACY（旧版 INeuralNetwork
 * 接口与新版不兼容，推论2：选择空间在结构上被裁剪）。
 * <p>
 * <b>提供者模式</b>：NN_LEGACY 插槽的产物是旧版 {@link NnFactory} 提供者。
 * <p>
 * 旧版删除时只需删本包（含本类），上层零改动。
 */
public final class LegacyNodeKeys {
    /** 神经网络插槽（旧版）：存放旧版 urana_process_original 的 nn（standard_bnn / bnn）。
     *  旧版 INeuralNetwork 接口与新版不兼容，故旧版 nn 在此独立插槽。 */
    public static final NodeKey<NnFactory> NN_LEGACY =
            new NodeKey<>(new ResourceLocation(SmarterTouhouMaids.MOD_ID, "nn_legacy"), NnFactory.class);

    private LegacyNodeKeys() {
    }
}
