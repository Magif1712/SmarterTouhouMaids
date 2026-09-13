package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Factory;

/**
 * AI 顶层工厂契约：树原生 {@link Factory}（概念树重构后）。
 * <p>
 * <b>工厂自驱组装</b>（真善美第1条"真"）：流程型 ai 的分支工厂从 {@code AssemblyContext}
 * 取自己声明的 process 子实例，组装 ProcessAiSystem。纯规则 ai 不声明 process child，
 * 直接造自己的 ai。
 */
public interface AiFactory extends Factory<IAiSystem> {
    @Override
    default Class<IAiSystem> producedType() {
        return IAiSystem.class;
    }
}
