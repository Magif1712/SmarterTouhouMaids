package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor;

/**
 * 感受器工厂：按感觉缓冲区尺寸创建一个 {@link ISensor} 实例。
 * <p>
 * <b>叶子工厂</b>（镜像 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.NnFactory}）：
 * 感受器是组装链的叶子，不查下层 registry（感受器之下无选择）。
 * <p>
 * <b>签名仅含感受器本征尺寸</b>（真善美第1条"真"）：feelingSize 是任何感受器实现都必需的本征参数
 * （决定捕获分辨率与位平面排布）。附属感受器若需额外超参数，自行读 Forge config，不经本签名传——
 * 避免为未到来的需求加抽象，保持签名纯粹与稳定。
 * <p>
 * feelingSize 由上层 agent factory 从 {@code ai.feelingSize()} 算出传入
 * （尺寸是 ai 层 Domain 知识，由 nn 的输入维度决定，不是感受器知识）。
 */
@FunctionalInterface
public interface SensorFactory {
    /**
     * @param feelingSize 感觉缓冲区尺寸（bits，由 ai.feelingSize() 算出传入）。
     * @return 创建好的 ISensor 实例（未 awaken，由 agent 后续调用 awaken 注入共享资源）。
     */
    ISensor create(int feelingSize);
}
