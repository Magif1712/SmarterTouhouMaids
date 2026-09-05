package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.original_mapper;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapperFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapperRegistryIds;
import net.minecraft.resources.ResourceLocation;

/**
 * urana 映射器模块向 MapperRegistry 贡献的注册入口（自包含）。
 * <p>
 * urana 是<b>核心默认 mapper</b>（新版架构 process→mapper→nn 中 mapper 层的唯一默认实现）。
 * 它的 entry 由 {@code AiModeDefaults.registerDefaults()} 经 {@link #mapperEntry(String)} 取用并注册，
 * 同时把 MapperRegistry 的默认 id 指向 {@link #MAPPER_ID}。
 * <p>
 * <b>nn 是 mapper 的附庸</b>（用户设计）：本 entry 的 subRegistryId 指向
 * {@link FittableMapperRegistryIds#ORIGINAL_MAPPER_NN}（per-mapper NN registry，只含 CNN），
 * 表示"选了 original_mapper 后只能选 CNN"——NN 选项与 mapper 载体天然配对。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：「urana 是一个自包含的 mapper 模块」这个模式落在 urana 包内
 *       （实现 + 注册贡献者）；AiModeDefaults 只决定「默认 mapper id」与「捆绑哪些 mapper 模块」，
 *       不内联 factory 或 id 字符串。</li>
 *   <li><b>第3条</b>：上层（urana/process）通过 MapperRegistry 取 FittableMapperFactory，不依赖本类；
 *       切换默认到别的 mapper 模块时只改 AiModeDefaults 一行，上层零改动。</li>
 *   <li><b>第4条</b>：「自包含」这个不实在约束，实在化为 {@link #mapperEntry(String)} 这个有签名的方法。</li>
 * </ul>
 */
public final class OriginalMapperModes {
    private OriginalMapperModes() {
    }

    /** MapperRegistry 中的稳定逻辑 id（lang/GUI 句柄）。 */
    public static final String MAPPER_ID = "original_mapper";

    /**
     * 构造 urana mapper 向 MapperRegistry 贡献的 entry。
     *
     * @param modId 模组 id（用于构造 ResourceLocation 与显示名 key）。
     * @return 非叶子 entry（subRegistryId=ORIGINAL_MAPPER_NN，选了 original_mapper 后只能选 CNN）。
     */
    public static RegistryEntry<FittableMapperFactory> mapperEntry(String modId) {
        return new RegistryEntry<>(
                new ResourceLocation(modId, MAPPER_ID),
                "mode." + modId + ".mapper.original_mapper",
                new OriginalMapperFactory(),
                FittableMapperRegistryIds.ORIGINAL_MAPPER_NN); // original_mapper 的专属 NN registry（只含 CNN）
    }
}