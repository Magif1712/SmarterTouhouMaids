package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Factory;

/**
 * 流程系统工厂契约：树原生 {@link Factory}（概念树重构后）。
 * <p>
 * <b>工厂自驱组装</b>：urana 取自己声明的 mapper 子实例；urana_original 取自己声明的
 * nn 提供者子实例（provider 模式：nn 尺寸是 process 的 Domain 知识，由 process 工厂
 * 经 encodingProfile+Domain 算出后带参实例化 nn）。
 * <p>
 * <b>per-maid 参数各取所需</b>（真善美第3条）：process 工厂经 {@code ctx.maid()} 查
 * ParamStore 读自己声明的参数（如 urana 快/慢环 minDt），nbtKey 自备——换 process 时
 * 外周与上层工厂零改动。
 */
public interface ProcessFactory extends Factory<IProcessSystem> {
    @Override
    default Class<IProcessSystem> producedType() {
        return IProcessSystem.class;
    }
}
