package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.standard_bnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;

/**
 * standard_bnn 的 {@link NnFactory} 实现：叶子工厂，创建带输入变化门控重连的 bnn 网络。
 * <p>
 * 不查下层 registry（nn 是组装链叶子）。尺寸由上层 process factory 算出传入。
 * <p>
 * 设计原则（真善美第3条）：把"可选门控重连 bnn"这个不实在的约束，实在化为工厂对象。
 * 注册到 NnRegistry 后，GUI 可选择 standard_bnn 模式，urana 零改动。
 */
public class StandardBnnNnFactory implements NnFactory {

    @Override
    public INeuralNetwork create(int inputSize, int outputSize) {
        return new StandardBnnNeuralNetwork(inputSize, outputSize);
    }
}
