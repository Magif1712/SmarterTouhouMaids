package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import net.minecraft.resources.ResourceLocation;

/**
 * Mapper 层（fittable_mapper）的 registry id 常量（真善美第2条：每层只决定其下一层，不感知更下层）。
 * <p>
 * 本类定义 mapper 层<b>决定的直接下层</b> id：{@link #NN}（神经网络 registry）。
 * mapper 层自身的 registry id（{@code smarter_touhou_maids:mapper}）由上层 process 层的
 * {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.UranaProcessRegistryIds#MAPPER}
 * 定义——父层决定子层 id，子层引用父层定义（上→下决定，下→上引用）。
 * <p>
 * 附属模组可在自己的 mapper 实现包内定义自己的直接下层 id，不需修改本类。
 */
public final class FittableMapperRegistryIds {
    /** 神经网络 registry（新版）：选哪个 nn 实现（cnn / ...），仅当上层 mapper 需要 nn 时展开。
     *  由 mapper 层决定（mapper 层的直接下层），nn 层引用此 id 注册 NnRegistry。 */
    public static final ResourceLocation NN = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "nn");

    private FittableMapperRegistryIds() {
    }
}
