package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import net.minecraft.resources.ResourceLocation;

/**
 * AI 层（process_ai）的 registry id 常量（真善美第2条：每层只决定其下一层，不感知更下层）。
 * <p>
 * 本类定义 AI 层<b>决定的直接下层</b> id：{@link #PROCESS}（流程系统 registry）。
 * AI 层自身的 registry id（{@code smarter_touhou_maids:ai}）由上层 agent 层的
 * {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryIds#AI}
 * 定义——父层决定子层 id，子层引用父层定义（上→下决定，下→上引用）。
 * <p>
 * 附属模组可在自己的 process_ai 实现包内定义自己的直接下层 id，不需修改本类——
 * 只要附属 ai entry 的 subRegistryId 指向附属自定义的 registry id，GUI 自动递归展开。
 */
public final class ProcessAiRegistryIds {
    /** 流程系统 registry（原初代理用）：选哪个 process 实现（urana_original / ...）。
     *  由 AI 层决定（AI 层的直接下层），process 层引用此 id 注册 ProcessRegistry。
     *  原初代理的 AI registry 的 process_ai entry 指向此 registry。 */
    public static final ResourceLocation PROCESS = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "process");
    /** 流程系统 registry（新版代理用）：只含 urana（新版流程）。
     *  与原初代理的 PROCESS registry 隔离（跨代理流程不可选，避免不兼容组合）。
     *  新版代理的 AI_SMARTER registry 的 process_ai entry 指向此 registry。 */
    public static final ResourceLocation PROCESS_SMARTER = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "process_smarter");

    private ProcessAiRegistryIds() {
    }
}
