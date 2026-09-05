package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import net.minecraft.resources.ResourceLocation;

/**
 * Mapper 层（fittable_mapper）的 registry id 常量（真善美第2条：每层只决定其下一层，不感知更下层）。
 * <p>
 * 本类定义各 mapper 外延的<b>专属 NN registry</b> id（per-mapper NN registry）。
 * mapper 层自身的 registry id（{@code smarter_touhou_maids:mapper}）由上层 process 层的
 * {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.UranaProcessRegistryIds#MAPPER}
 * 定义——父层决定子层 id，子层引用父层定义（上→下决定，下→上引用）。
 * <p>
 * <b>per-mapper NN registry</b>（设计原则2）：NN 是 mapper 的模式，不同 mapper 外延只能看到
 * 与自己载体兼容的 NN 子集。original_mapper（FloatVector 载体）只能配 CNN；bnn_mapper
 * （BoolVector 载体）只能配 BNN。每个 mapper entry 指向自己的 NN registry，GUI 递归时
 * {@code registry.getAllIds()} 自然只返回该 mapper 兼容的 NN 选项——切换 mapper 时
 * NN 选项自动切换，上层（process）零改动。
 * <p>
 * 附属模组可在自己的 mapper 实现包内定义自己的 NN registry id，不需修改本类。
 */
public final class FittableMapperRegistryIds {
    /** original_mapper 的专属 NN registry：只注册 CNN（FloatVector 载体，与 original_mapper 兼容）。 */
    public static final ResourceLocation ORIGINAL_MAPPER_NN = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "original_mapper_nn");
    /** bnn_mapper 的专属 NN registry：只注册 BNN（BoolVector 载体，与 bnn_mapper 兼容）。 */
    public static final ResourceLocation BNN_MAPPER_NN = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "bnn_mapper_nn");

    private FittableMapperRegistryIds() {
    }
}