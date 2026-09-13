package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.original_mapper;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapperNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Branch;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Meta;
import net.minecraft.resources.ResourceLocation;

/**
 * urana 映射器模块向 MAPPER 插槽贡献的注册入口（自包含）。
 * <p>
 * original_mapper 是<b>核心默认 mapper</b>（FloatVector 载体）。它的 Branch 由 process 层
 * 注册处经 {@link #mapperBranch(String)} 取用并注册，同时把 MAPPER 插槽默认指向 {@link #MAPPER_ID}。
 * <p>
 * <b>nn 是 mapper 的附庸</b>（推论2 的结构化表达）：本 Branch 声明 child "nn" →
 * {@link FittableMapperNodeKeys#ORIGINAL_MAPPER_NN}（per-mapper NN 插槽，只含 CNN），
 * 表示"选了 original_mapper 后只能选 CNN"——NN 选项与 mapper 载体天然配对。
 */
public final class OriginalMapperModes {
    private OriginalMapperModes() {
    }

    /** MAPPER 插槽中的稳定逻辑 id（lang/GUI 句柄）。 */
    public static final String MAPPER_ID = "original_mapper";

    /**
     * 构造 original_mapper 向 MAPPER 插槽贡献的 Branch（含 nn child → 其专属 NN 插槽）。
     *
     * @param modId 模组 id（用于构造 ResourceLocation 与显示名 key）。
     */
    public static Branch<FittableMapper> mapperBranch(String modId) {
        Branch<FittableMapper> branch = new Branch<>(
                new ResourceLocation(modId, MAPPER_ID),
                new OriginalMapperFactory(),
                new Meta("mode." + modId + ".mapper.original_mapper", 0, modId));
        branch.addChild("nn", FittableMapperNodeKeys.ORIGINAL_MAPPER_NN);
        return branch;
    }
}
