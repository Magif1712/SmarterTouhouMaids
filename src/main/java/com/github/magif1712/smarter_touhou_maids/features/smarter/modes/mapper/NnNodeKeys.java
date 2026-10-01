package com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.assembly.NodeKey;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.NnFactory;
import net.minecraft.resources.ResourceLocation;

/**
 * Mapper 层为各分支决定的<b>专属下层（nn）插槽</b>键常量（真善美第2条：每层只决定其下一层）。
 * <p>
 * <b>归属（W21 归位，修正 W7 的偏差）</b>：按"<b>父层决定子层 id</b>"，nn 各插槽的 id
 * （{@code bnn_mapper_nn} 等）是 <b>mapper 层</b>为其每个分支决定的，故本类住在
 * <b>mapper 层自己的包</b>（{@code smarter/mapper/}）。W7 曾把它们聚到 {@code mapper/nn/}
 * 并写成"由 NN 层自己定义"——聚拢本身对了（审计 ❸），但归属层错了：<b>决定者才是持有者</b>。
 * <p>
 * <b>per-mapper 专属插槽</b>（推论2 的结构化裁剪）：每个 mapper 只能看到与自己载体兼容的 NN 子集——
 * {@code original_mapper}（FloatVector）只见 CNN；{@code bnn_mapper}（BoolVector）只见 BNN；
 * {@code cnn_active_mapper}（FloatVector + 奇激活）只见 cnn_active。
 * <p>
 * 附属模组可在自己的 NN 实现包内定义自己的插槽键，不需修改本类。
 */
public final class NnNodeKeys {
    /** original_mapper 的专属 NN 插槽：只注册 CNN（FloatVector 载体，与 original_mapper 兼容）。 */
    public static final NodeKey<NnFactory> ORIGINAL_MAPPER_NN =
            new NodeKey<>(new ResourceLocation(SmarterTouhouMaids.MOD_ID, "original_mapper_nn"), NnFactory.class);
    /** bnn_mapper 的专属 NN 插槽：只注册 BNN（BoolVector 载体，与 bnn_mapper 兼容）。 */
    public static final NodeKey<NnFactory> BNN_MAPPER_NN =
            new NodeKey<>(new ResourceLocation(SmarterTouhouMaids.MOD_ID, "bnn_mapper_nn"), NnFactory.class);
    /** cnn_active_mapper 的专属 NN 插槽：只注册 cnn_active（FloatVector 载体 + 奇激活，与本 mapper 兼容）。 */
    public static final NodeKey<NnFactory> CNN_ACTIVE_MAPPER_NN =
            new NodeKey<>(new ResourceLocation(SmarterTouhouMaids.MOD_ID, "cnn_active_mapper_nn"), NnFactory.class);

    private NnNodeKeys() {
    }
}
