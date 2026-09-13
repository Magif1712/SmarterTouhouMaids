package com.github.magif1712.smarter_touhou_maids.features.smarter.agent;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Factory;

/**
 * Agent 顶层工厂契约：树原生 {@link Factory}（概念树重构后）。
 * <p>
 * <b>工厂自驱组装</b>（真善美第1条"真"）：每层 factory 只从 {@code AssemblyContext} 取
 * 自己 Branch 声明过的具名子实例（如 agent 分支取 ai/sensor/effector），不感知更下层。
 * 外周（SmarterClientService）只调 Resolver，不感知任何插槽的存在。
 * <p>
 * 附属 agent 工厂实现本接口即可（纯规则 agent 不声明 ai/sensor/effector children，
 * 直接 new 自己的 agent）。
 */
public interface AgentFactory extends Factory<IAgent> {
    @Override
    default Class<IAgent> producedType() {
        return IAgent.class;
    }
}
