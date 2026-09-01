package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector;

/**
 * 效应器工厂：按行为向量尺寸创建一个 {@link IEffector} 实例。
 * <p>
 * <b>叶子工厂</b>（镜像 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.NnFactory}
 * 与 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.SensorFactory}）：
 * 效应器是组装链的叶子，不查下层 registry（效应器之下无选择）。
 * <p>
 * <b>签名仅含效应器本征尺寸</b>（真善美第1条"真"）：behaviorSize 是任何效应器实现都必需的本征参数
 * （决定行为向量位宽与肌群布局）。附属效应器若需额外超参数，自行读 Forge config，不经本签名传——
 * 避免为未到来的需求加抽象，保持签名纯粹与稳定。
 * <p>
 * behaviorSize 由上层 agent factory 从 {@code ai.behaviorSize()} 算出传入
 * （尺寸是 ai 层 Domain 知识，由 nn 的输出维度决定，不是效应器知识）。
 */
@FunctionalInterface
public interface EffectorFactory {
    /**
     * @param behaviorSize 行为向量尺寸（bits，由 ai.behaviorSize() 算出传入）。
     * @return 创建好的 IEffector 实例（未 awaken，由 agent 后续调用 awaken 初始化肌肉状态）。
     */
    IEffector create(int behaviorSize);
}
