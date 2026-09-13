package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.NodeKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Process 层（urana_process）的插槽键常量（真善美第2条：每层只决定其下一层，不感知更下层）。
 * <p>
 * 本类定义 process 层<b>决定的直接下层</b>插槽：{@link #MAPPER}（可拟合映射器插槽）。
 * process 层自身的插槽键由上层 AI 层的 {@code ProcessAiNodeKeys#PROCESS} 定义——
 * 父层决定子层 id，子层引用父层定义（上→下决定，下→上引用）。
 * <p>
 * 附属模组可在自己的 process 实现包内定义自己的直接下层插槽（如 process→custom_layer→nn），
 * 不需修改本类——只要附属 process Branch 的 children 指向附属自定义的插槽，GUI 自动递归展开。
 */
public final class UranaProcessNodeKeys {
    /** 可拟合映射器插槽：选哪个 mapper 实现（original_mapper / bnn_mapper / ...），
     *  仅当上层 process 分支（urana）声明了该 child 时展开。
     *  由 process 层决定（process 层的直接下层），mapper 层引用此键挂 Branch。 */
    public static final NodeKey<FittableMapper> MAPPER =
            new NodeKey<>(new ResourceLocation(SmarterTouhouMaids.MOD_ID, "mapper"), FittableMapper.class);

    private UranaProcessNodeKeys() {
    }
}
