package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.NodeKey;
import net.minecraft.resources.ResourceLocation;

/**
 * cnn_active_mapper 的<b>专属 NN 插槽</b>键（per-mapper NN 插槽，推论2 的结构化表达）。
 * <p>
 * 与 {@code FittableMapperNodeKeys} 的关系（真善美第2条：每层只决定其下一层，不感知更下层）：
 * <ul>
 *   <li>mapper 层自身的插槽键（{@code MAPPER}）由上层 process 层定义——
 *       父层决定子层 id，子层引用父层定义。</li>
 *   <li>本 mapper 的专属 NN 插槽键由 mapper 子层（= 本模块）自己定义——
 *       本模块是自包含的，不修改上层任何文件（方案 §6.3：既有文件仅 CMakeLists 与 lang 需触碰）。</li>
 * </ul>
 * 结构效果：{@code cnn_active_mapper} 的 {@code child("nn")} 指向本插槽，本插槽只注册
 * {@code cnn_active} ⟹ FloatVector 载体 + 奇激活的组合在结构上被锁定；
 * 切换 mapper 时 NN 选项自动切换，上层零改动。
 */
public final class CnnActiveMapperNodeKeys {
    /** cnn_active_mapper 的专属 NN 插槽：只注册 cnn_active（FloatVector 载体，与本 mapper 兼容）。 */
    public static final NodeKey<NnFactory> CNN_ACTIVE_MAPPER_NN =
            new NodeKey<>(new ResourceLocation(SmarterTouhouMaids.MOD_ID, "cnn_active_mapper_nn"), NnFactory.class);

    private CnnActiveMapperNodeKeys() {
    }
}
