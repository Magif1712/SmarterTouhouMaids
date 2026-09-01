package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.CnnNnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;

/**
 * cnn 模块向 NnRegistry 贡献的注册入口（自包含）。
 * <p>
 * cnn 是<b>核心默认 nn</b>（新版架构 process→mapper→nn 中 nn 层的唯一默认实现，
 * 即朴素 CNN——BNN 的浮点版）。它的 entry 由 {@code AiModeDefaults.registerDefaults()}
 * 经 {@link #nnEntry(String)} 取用并注册，同时把 NnRegistry 的默认 id 指向 {@link #NN_ID}。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：「cnn 是一个自包含的 nn 模块」这个模式落在 cnn 包内
 *       （实现 + 注册贡献者）；AiModeDefaults 只决定「默认 nn id」与「捆绑哪些 nn 模块」，
 *       不内联 factory 或 id 字符串。</li>
 *   <li><b>第3条</b>：上层（mapper/process）通过 NnRegistry 取 NnFactory，不依赖本类；
 *       切换默认到别的 nn 模块时只改 AiModeDefaults 一行，上层零改动。</li>
 *   <li><b>第4条</b>：「自包含」这个不实在约束，实在化为 {@link #nnEntry(String)} 这个有签名的方法。</li>
 * </ul>
 */
public final class CnnNnModes {
    private CnnNnModes() {
    }

    /** NnRegistry 中的稳定逻辑 id（存档/lang/GUI 句柄）。 */
    public static final String NN_ID = "cnn";

    /**
     * 构造 cnn 向 NnRegistry 贡献的 entry。
     *
     * @param modId 模组 id（用于构造 ResourceLocation 与显示名 key）。
     * @return 叶子 entry（subRegistryId=null，nn 之下无选择）。
     */
    public static RegistryEntry<NnFactory> nnEntry(String modId) {
        return new RegistryEntry<>(
                new ResourceLocation(modId, NN_ID),
                "mode." + modId + ".nn.cnn",
                new CnnNnFactory(),
                null); // 叶子，无下层
    }
}
