package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.bnn_mapper;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapperNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Branch;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Meta;
import net.minecraft.resources.ResourceLocation;

/**
 * bnn_mapper 模块向 MAPPER 插槽贡献的注册入口（自包含）。
 * <p>
 * bnn_mapper 是 BNN 载体（BoolVector 位运算）的映射器实现，与 original_mapper（FloatVector 载体）
 * 并列，供 GPU 占用对照测试切换。
 * <p>
 * <b>非默认</b>：默认 mapper 仍是 original_mapper；本 Branch 仅注册为可选项，
 * 测试时在 GUI 手动切换 mapper 到 {@link #MAPPER_ID}（NN 自动切换到 BNN）。
 * <p>
 * <b>nn 是 mapper 的附庸</b>（推论2 的结构化表达）：本 Branch 声明 child "nn" →
 * {@link FittableMapperNodeKeys#BNN_MAPPER_NN}（per-mapper NN 插槽，只含 BNN）。
 */
public final class BnnMapperModes {
    private BnnMapperModes() {
    }

    /** MAPPER 插槽中的稳定逻辑 id（lang/GUI 句柄）。 */
    public static final String MAPPER_ID = "bnn_mapper";

    /**
     * 构造 bnn_mapper 向 MAPPER 插槽贡献的 Branch（含 nn child → 其专属 NN 插槽）。
     *
     * @param modId 模组 id（用于构造 ResourceLocation 与显示名 key）。
     */
    public static Branch<FittableMapper> mapperBranch(String modId) {
        Branch<FittableMapper> branch = new Branch<>(
                new ResourceLocation(modId, MAPPER_ID),
                new BnnMapperFactory(),
                new Meta("mode." + modId + ".mapper.bnn_mapper", 0, modId));
        branch.addChild("nn", FittableMapperNodeKeys.BNN_MAPPER_NN);
        return branch;
    }
}
