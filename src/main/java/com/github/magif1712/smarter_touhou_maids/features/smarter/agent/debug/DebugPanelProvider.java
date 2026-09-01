package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamOption;

import java.util.List;

/**
 * Factory 的可选能力契约：暴露该层模式的可调试项列表（{@link ParamOption}，
 * controlHint="toggle"），供 GUI（AgentDebugPanel）数据驱动渲染。
 * <p>
 * <b>与各层 Factory 正交</b>（真善美第1条"真"：每层只注入自己直接使用的那个抽象）：
 * Factory 关心"如何创建实例"，本接口关心"有哪些调试项"（数据来源）。
 * 变化轴不同：Factory 变化是"换实现"，本接口变化是"换调试项集合"。
 * <p>
 * <b>Factory 级别而非 agent 实例级别</b>：调试项状态存 ParamStore（maid NBT，per-maid，随存档走），
 * 不依赖运行实例，且需要在附身前（未创建 agent 实例时）就能配置。Factory 在注册时就存在（不需要附身），
 * 故由 Factory 实现本接口。AgentDebugPanel 从各层 registry 取当前选中的 entry 的 factory，
 * 检查是否实现本接口，汇总所有层的调试项。
 * <p>
 * <b>统一参数管道</b>（真善美第2/4条）：调试项复用 {@link ParamOption}（controlHint="toggle"），
 * per-maid 存 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamStore}。
 * 不再用专用的 DebugOption——DebugOption 是 ParamOption 的严格子集（布尔 = text + controlHint="toggle"），
 * 统一为 ParamOption 后消除第二套声明机制，控件可换（controlHint/controlMeta）。
 * <p>
 * <b>与 ParamPanelProvider 的区别</b>：两者都返回 {@code List<ParamOption>}，但语义不同——
 * ParamPanelProvider 返回参数项（controlHint 可为 "text"/"toggle"/"slider"，值类型多样），
 * 本接口返回调试项（controlHint 固定 "toggle"，纯布尔开关）。两者分别由
 * RuntimeParamsPanel / AgentDebugPanel 消费，呈现分离（参数面板 / 调试面板）。
 * <p>
 * <b>随各层模式动态切换</b>（真善美第2条）：遍历 agent→ai→process→nn 递归链 + sensor/effector 叶子，
 * 每层选中的 entry 的 factory 若实现本接口即渲染其调试项。
 * 选 sensor=possession_sensor → PossessionSensorFactory 的调试项；
 * 换 sensor=其他 → 那个 sensor factory 的调试项（若实现本接口）。
 * 用户在 ModeSelectorPanel 切换某层模式后 rebuildWidgets → 本 Panel 重新遍历 → 调试项动态切换。
 * 不实现本接口的 factory 自动跳过，GUI 零改动。
 */
public interface DebugPanelProvider {
    /**
     * 该 Factory 暴露的可调试项列表（controlHint="toggle" 的 ParamOption）。
     * <p>
     * 返回空列表 = 无调试项。实现应稳定（每次调用返回等价内容），供 GUI 在 init/rebuildWidgets 时反复读取。
     *
     * @return 调试项列表（不可变视图为宜）
     */
    List<ParamOption> getDebugOptions();
}
