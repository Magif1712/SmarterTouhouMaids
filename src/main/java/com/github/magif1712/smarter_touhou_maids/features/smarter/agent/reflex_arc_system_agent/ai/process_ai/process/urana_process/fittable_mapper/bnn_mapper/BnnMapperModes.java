package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.bnn_mapper;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapperFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapperRegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;

/**
 * bnn_mapper 模块向 MapperRegistry 贡献的注册入口（自包含）。
 * <p>
 * bnn_mapper 是 BNN 载体（BoolVector 位运算）的映射器实现，与
 * {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.original_mapper.OriginalMapperModes}
 * （original_mapper，FloatVector 载体）并列，供 GPU 占用对照测试切换。
 * <p>
 * <b>非默认</b>：默认 mapper 仍是 original_mapper；本 entry 仅注册为可选项，
 * 测试时在 GUI 手动切换 mapper 到 {@link #MAPPER_ID}（NN 自动切换到 BNN）。
 * <p>
 * <b>nn 是 mapper 的附庸</b>（用户设计）：本 entry 的 subRegistryId 指向
 * {@link FittableMapperRegistryIds#BNN_MAPPER_NN}（per-mapper NN registry，只含 BNN），
 * 表示"选了 bnn_mapper 后只能选 BNN"——NN 选项与 mapper 载体天然配对。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：「bnn_mapper 是一个自包含的 mapper 模块」这个模式落在 bnn_mapper 包内
 *       （实现 + 注册贡献者）。</li>
 *   <li><b>第3条</b>：上层（urana/process）通过 MapperRegistry 取 FittableMapperFactory，不依赖本类；
 *       切换或删除本模块时上层零改动——"上层 ai 系统中的映射器可以切换到 bnn_mapper 也可以切换到 original_mapper"。</li>
 *   <li><b>第4条</b>：「自包含」这个不实在约束，实在化为 {@link #mapperEntry(String)} 这个有签名的方法。</li>
 * </ul>
 */
public final class BnnMapperModes {
    private BnnMapperModes() {
    }

    /** MapperRegistry 中的稳定逻辑 id（lang/GUI 句柄）。 */
    public static final String MAPPER_ID = "bnn_mapper";

    /**
     * 构造 bnn_mapper 向 MapperRegistry 贡献的 entry。
     *
     * @param modId 模组 id（用于构造 ResourceLocation 与显示名 key）。
     * @return 非叶子 entry（subRegistryId=BNN_MAPPER_NN，选了 bnn_mapper 后只能选 BNN）。
     */
    public static RegistryEntry<FittableMapperFactory> mapperEntry(String modId) {
        return new RegistryEntry<>(
                new ResourceLocation(modId, MAPPER_ID),
                "mode." + modId + ".mapper.bnn_mapper",
                new BnnMapperFactory(),
                FittableMapperRegistryIds.BNN_MAPPER_NN); // bnn_mapper 的专属 NN registry（只含 BNN）
    }
}