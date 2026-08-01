package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector;

/**
 * 效应器的机械级抽象边界（agent 的"肌肉"）。
 * <p>
 * 镜像 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.IAiSystem}
 * （思考机制契约）与 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.ISensor}
 * （感受器契约）：上层 agent 持本接口引用，效应器实现可替换，换效应器时 agent 零改动。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：agent 意识域 C 只有"感知→思考→行动"的行动环节，不含效应器内部细节
 *       （拮抗肌对/极化布局/张力积分/迟滞阈值）。这些效应器内部模式必须藏在实现里，
 *       agent 通过本接口访问。</li>
 *   <li><b>第3条</b>：把"可替换效应器"这个不实在的约束，用实在的接口（有签名的方法）固化。</li>
 * </ul>
 * <p>
 * <b>产出契约</b>：所有效应器实现都产出同一 {@link ActionIntent}（效应器产出、服务端固定消费），
 * 故 {@code execution/}（服务端执行端）留在 effector 顶层，不随效应器实现变而变——这是
 * "模式1（ActionIntent 执行）即便换了一种模式2（效应器实现）也可以不改代码就可以正确运行"。
 *
 * @see com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.bionic_muscle_effector.BionicMuscleEffector
 */
public interface IEffector {

    /**
     * 初始化肌肉状态。由 agent 在 awaken 时调用（在 ai.awaken 之前，效应器就绪即可解码）。
     */
    void awaken();

    /**
     * 把一帧 bit-packed 行为向量解码为操作要求。
     * <p>
     * 返回的 {@link ActionIntent} 通常是复用实例，下次 tick 后失效。调用方应立即消费
     * （如序列化发包），不要跨 tick 持有引用。
     *
     * @param packedBehavior bit-packed 行为向量（int[]，LSB-first）。
     * @return 解码后的操作要求（复用实例）。
     */
    ActionIntent tick(int[] packedBehavior);

    /**
     * 复位肌肉状态（张力/迟滞等有状态量）。由 agent 在 shutdown 时调用。
     */
    void shutdown();
}
