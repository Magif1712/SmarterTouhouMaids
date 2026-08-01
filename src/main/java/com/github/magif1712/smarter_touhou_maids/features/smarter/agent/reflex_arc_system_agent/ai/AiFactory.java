package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai;

import net.minecraft.nbt.CompoundTag;

/**
 * AI 顶层工厂：按配置创建一个 {@link IAiSystem} 实例。
 * <p>
 * <b>工厂自驱组装</b>（真善美第1条"真"：每层 factory 只注入自己直接使用的下层抽象）：
 * 本工厂直接使用 process（ProcessAiSystem 构造注入 IProcessSystem），故自行查
 * {@code ProcessRegistry} 取下层 process factory 并创建 process，再 new ProcessAiSystem(process)。
 * 外周（SmarterClientService）不感知下层 registry 的存在——它只调本工厂。
 * <p>
 * <b>config 各取所需</b>：config 是一个 CompoundTag，含所有层的选择 id（key = registryId.toString()）
 * 与各层特定参数。本工厂只读自己需要的 key（processId），忽略其余。
 * <p>
 * 纯规则 ai 的工厂实现不查 ProcessRegistry，直接 new RuleBasedAi(...)（config 里读自己需要的参数）。
 */
@FunctionalInterface
public interface AiFactory {
    /**
     * @param config 配置载体（含各层 mode id + 各层特定参数），各 factory 各取所需。
     * @return 创建好的 IAiSystem 实例（已注入下层，可直接 awaken）。
     */
    IAiSystem create(CompoundTag config);
}
