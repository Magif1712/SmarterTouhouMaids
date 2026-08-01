package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param;

import java.util.List;

/**
 * Factory 的可选能力契约：暴露该层模式的可调参数列表，供 GUI（RuntimeParamsPanel）数据驱动渲染。
 * <p>
 * 与 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug.DebugPanelProvider}
 * 完全对称：DebugPanelProvider 暴露调试项（开关，static/单例状态）；
 * ParamPanelProvider 暴露参数项（数值，per-maid 状态）。
 * <p>
 * <b>随各层模式动态切换</b>（真善美第2条）：选 process=urana → UranaProcessFactory 暴露快/慢环参数；
 * 换 process=其他 → 那个 process factory 的参数（若实现本接口）。
 * 不实现本接口的 factory 自动跳过，GUI 零改动。
 * <p>
 * <b>消除硬编码参数</b>：参数跟随各层选中模式动态出现/消失——
 * neuronCount 之类的全局参数已由各层实现内部硬编码最优值，不再需要 UI 暴露。
 */
public interface ParamPanelProvider {
    /**
     * 该 Factory 暴露的可调参数列表（per-maid）。
     *
     * @return 参数项列表（空列表 = 无可调参数）
     */
    List<ParamOption> getParamOptions();
}
