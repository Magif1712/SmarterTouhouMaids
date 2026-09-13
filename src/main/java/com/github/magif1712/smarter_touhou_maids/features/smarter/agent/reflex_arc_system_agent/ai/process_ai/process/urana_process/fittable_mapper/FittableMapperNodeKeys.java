package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.NodeKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Mapper 层（fittable_mapper）的插槽键常量（真善美第2条：每层只决定其下一层，不感知更下层）。
 * <p>
 * 本类定义各 mapper 外延的<b>专属 NN 插槽</b>键（per-mapper NN 插槽，推论2 的结构化表达）。
 * mapper 层自身的插槽键由上层 process 层的 {@code UranaProcessNodeKeys#MAPPER} 定义——
 * 父层决定子层 id，子层引用父层定义（上→下决定，下→上引用）。
 * <p>
 * <b>per-mapper NN 插槽</b>（设计原则2 + 推论2）：NN 是 mapper 的模式，不同 mapper 外延只能看到
 * 与自己载体兼容的 NN 子集。original_mapper（FloatVector）只能配 CNN；bnn_mapper（BoolVector）
 * 只能配 BNN。每个 mapper Branch 的 children 指向自己的 NN 插槽，选择空间在结构上被裁剪
 * （切换 mapper 时 NN 选项自动切换，上层零改动）。
 * <p>
 * <b>提供者模式</b>：NN 插槽的产物是 {@link NnFactory} 提供者——nn 实例化需要尺寸参数
 * （来自 mapper 工厂经 encodingProfile+IODomain 计算），由消费方父工厂带参实例化。
 * <p>
 * 附属模组可在自己的 mapper 实现包内定义自己的 NN 插槽键，不需修改本类。
 */
public final class FittableMapperNodeKeys {
    /** original_mapper 的专属 NN 插槽：只注册 CNN（FloatVector 载体，与 original_mapper 兼容）。 */
    public static final NodeKey<NnFactory> ORIGINAL_MAPPER_NN =
            new NodeKey<>(new ResourceLocation(SmarterTouhouMaids.MOD_ID, "original_mapper_nn"), NnFactory.class);
    /** bnn_mapper 的专属 NN 插槽：只注册 BNN（BoolVector 载体，与 bnn_mapper 兼容）。 */
    public static final NodeKey<NnFactory> BNN_MAPPER_NN =
            new NodeKey<>(new ResourceLocation(SmarterTouhouMaids.MOD_ID, "bnn_mapper_nn"), NnFactory.class);

    private FittableMapperNodeKeys() {
    }
}
