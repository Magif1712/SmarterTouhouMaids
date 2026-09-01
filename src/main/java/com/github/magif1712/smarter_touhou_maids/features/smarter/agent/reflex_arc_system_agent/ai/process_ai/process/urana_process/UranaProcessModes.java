package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.ProcessFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.UranaProcessRegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.UranaProcessFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;

/**
 * urana 流程模块向 ProcessRegistry 贡献的注册入口（自包含）。
 * <p>
 * urana 是<b>核心默认 process</b>（新版架构 process→mapper→nn 中 process 层的唯一默认实现）。
 * 它的 entry 由 AI 层 {@code ProcessAiRegistration}（@EventBusSubscriber）经 {@link #processEntry(String)}
 * 取用并注册，同时把 ProcessRegistry 的默认 id 指向 {@link #PROCESS_ID}。
 * <p>
 * <b>mapper 是 process 的直接下层</b>：本 entry 的 subRegistryId 指向
 * {@link UranaProcessRegistryIds#MAPPER}，表示"选了 urana process 后还要选 mapper"——
 * mapper registry 在 process 之下，由 process entry 带出。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：「urana 是一个自包含的 process 模块」这个模式落在 urana_process 包内
 *       （实现 + 注册贡献者）；AI 层 ProcessAiRegistration 只决定「默认 process id」与
 *       「捆绑哪些 process 模块」，不内联 factory 或 id 字符串。</li>
 *   <li><b>第3条</b>：上层（AI）通过 ProcessRegistry 取 ProcessFactory，不依赖本类；
 *       切换默认到别的 process 模块时只改注册代码一行，上层零改动。</li>
 * </ul>
 */
public final class UranaProcessModes {
    private UranaProcessModes() {
    }

    /** ProcessRegistry 中的稳定逻辑 id（存档/lang/GUI 句柄）。 */
    public static final String PROCESS_ID = "urana";

    /**
     * 构造 urana process 向 ProcessRegistry 贡献的 entry。
     *
     * @param modId 模组 id（用于构造 ResourceLocation 与显示名 key）。
     * @return 非叶子 entry（subRegistryId=MAPPER，选了 process 后还要选 mapper）。
     */
    public static RegistryEntry<ProcessFactory> processEntry(String modId) {
        return new RegistryEntry<>(
                new ResourceLocation(modId, PROCESS_ID),
                "mode." + modId + ".process.urana",
                new UranaProcessFactory(),
                UranaProcessRegistryIds.MAPPER); // process 之下还要选 mapper
    }
}
