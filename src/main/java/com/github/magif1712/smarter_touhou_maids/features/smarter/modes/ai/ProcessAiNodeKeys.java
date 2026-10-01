package com.github.magif1712.smarter_touhou_maids.features.smarter.modes.ai;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.process.IProcessSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.assembly.NodeKey;
import net.minecraft.resources.ResourceLocation;

/**
 * AI 层的插槽键常量（真善美第2条：每层只决定其下一层，不感知更下层）。
 * <p>
 * <b>归属（W21 归位）</b>：本类声明的是 AI 层<b>决定的直接下层</b>插槽 {@link #PROCESS}，
 * 故它住在 <b>AI 层自己的包</b>（{@code smarter/ai/}）——"父层决定子层 id"意味着插槽键由父层持有。
 * 此前它住在 {@code process_ai/}（AI 层的<b>分支实现</b>包）里，等于让一个分支实现代表整层决定下层
 * （违背定理1(4) 的平级放置）。
 * <p>
 * AI 层自身的插槽键由上层 agent 层的 {@code AgentNodeKeys.AI} 定义——父层决定子层 id，
 * 子层引用父层定义（上→下决定，下→上引用）。
 * <p>
 * 附属模组可在自己的 ai 实现包内定义自己的直接下层插槽，不需修改本类——
 * 只要附属 ai Branch 的 children 指向附属自定义的插槽，GUI 自动递归展开。
 */
public final class ProcessAiNodeKeys {
    /** 流程系统插槽：选哪个 process 实现（urana[新] / urana_original[旧] / 附属的别的流程）。
     *  由 AI 层决定（AI 层的直接下层），process 层引用此键挂 Branch。 */
    public static final NodeKey<IProcessSystem> PROCESS =
            new NodeKey<>(new ResourceLocation(SmarterTouhouMaids.MOD_ID, "process"), IProcessSystem.class);

    private ProcessAiNodeKeys() {
    }
}
