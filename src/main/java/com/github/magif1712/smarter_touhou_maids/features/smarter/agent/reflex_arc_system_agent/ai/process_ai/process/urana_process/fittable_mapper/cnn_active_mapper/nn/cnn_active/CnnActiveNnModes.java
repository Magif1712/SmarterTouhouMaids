package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper.nn.cnn_active;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Branch;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Meta;
import net.minecraft.resources.ResourceLocation;

/**
 * cnn_active 模块向自己的 NN 插槽贡献的注册入口（自包含）。
 * <p>
 * 与 {@code CnnNnModes}（NN_ID = {@code "cnn"}）对称，但本模块<b>不</b>占用
 * {@code ORIGINAL_MAPPER_NN} 插槽——它有自己的专属插槽
 * （{@code CnnActiveMapperNodeKeys.CNN_ACTIVE_MAPPER_NN}），由 {@code CnnActiveMapperModes}
 * 的 {@code child("nn")} 声明指向（推论2 的结构化裁剪：选了 cnn_active_mapper 才看得到 cnn_active）。
 * <p>
 * 设计原则（真善美）：「cnn_active 是一个自包含的 nn 模块」这个模式落在本包内
 * （实现 + 注册贡献者）；上层只决定"默认 nn id"，不内联 factory 或 id 字符串。
 */
public final class CnnActiveNnModes {
    private CnnActiveNnModes() {
    }

    /** NN 插槽中的稳定逻辑 id（存档/lang/GUI 句柄）。 */
    public static final String NN_ID = "cnn_active";

    /**
     * 构造 cnn_active 向专属 NN 插槽贡献的 Branch。
     *
     * @param modId 模组 id（用于构造 ResourceLocation 与显示名 key）。
     * @return 叶子 Branch（无 children，nn 之下无选择）。
     */
    public static Branch<NnFactory> nnBranch(String modId) {
        return new Branch<>(
                new ResourceLocation(modId, NN_ID),
                new CnnActiveNnFactory(),
                new Meta("mode." + modId + ".nn.cnn_active", 0, modId));
    }
}
