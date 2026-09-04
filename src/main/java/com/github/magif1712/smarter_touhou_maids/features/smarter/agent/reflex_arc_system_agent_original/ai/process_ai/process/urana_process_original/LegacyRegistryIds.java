package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import net.minecraft.resources.ResourceLocation;

/**
 * 旧版 urana_process_original 层的 registry id 常量（真善美第2条：每层只决定其下一层，不感知更下层）。
 * <p>
 * 本类定义旧版层<b>决定的直接下层</b> id：{@link #NN_LEGACY}（旧版神经网络 registry）。
 * 旧版层自身的 registry id（{@code smarter_touhou_maids:process}）由上层 AI 层的
 * {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.ProcessAiRegistryIds#PROCESS}
 * 定义——旧版 urana_original entry 追加进 ProcessRegistry（与新版 urana entry 共用 process 层），
 * 但其 subRegistryId 指向独立的 NN_LEGACY（旧版 INeuralNetwork 接口与新版不兼容）。
 * <p>
 * 旧版删除时只需删本类与 urana_process_original 包，上层零改动。
 */
public final class LegacyRegistryIds {
    /** 神经网络 registry（旧版）：存放旧版 urana_process_original 的 nn（standard_bnn / bnn）。
     *  旧版 INeuralNetwork 接口与新版不兼容，故旧版 nn 迁到此独立 registry。 */
    public static final ResourceLocation NN_LEGACY = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "nn_legacy");

    private LegacyRegistryIds() {
    }
}
