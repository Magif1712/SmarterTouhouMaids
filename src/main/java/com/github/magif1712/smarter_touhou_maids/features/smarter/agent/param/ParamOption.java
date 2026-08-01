package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param;

import net.minecraft.network.chat.Component;

/**
 * 单个参数项的顶层契约（sealed）：一个 agent 暴露给 GUI 的可配置项。
 * <p>
 * 与 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug.DebugOption} 对称：
 * DebugOption 的 getter/setter 是 static/单例（不依赖 maid）；
 * ParamOption 的 getter/setter 接收 maid（per-maid 状态）。
 * <p>
 * <b>值类型多态</b>（真善美第2条）：意识域 C 中"agent 的 per-maid 可配置项"是一个模式，
 * 值类型（数值/布尔）是该模式下的两种实例。代码域 D 用 sealed 契约 + 两个 permits：
 * <ul>
 *   <li>{@link LongParamOption}：数值参数（如快/慢环 minDt），EditBox 渲染。</li>
 *   <li>{@link BoolParamOption}：布尔开关（如允许附身），CycleButton 渲染。</li>
 * </ul>
 * RuntimeParamsPanel 用 instanceof 分支渲染，新增值类型只需新增 permits 子类 + 一个渲染分支。
 * <p>
 * 真善美第3条：把"该层有哪些可调参数、各是什么值类型"这个不实在的概念，
 * 实在化为有签名的 sealed 数据结构。
 */
public sealed interface ParamOption permits LongParamOption, BoolParamOption {
    /** 参数项标签（i18n）。 */
    Component label();

    /** 参数项 tooltip（i18n）。 */
    Component tooltip();
}
