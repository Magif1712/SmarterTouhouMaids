package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;

/**
 * BNN 的 {@link NnFactory} 实现：叶子工厂，直接 new BnnNeuralNetwork。
 * <p>
 * 不查下层 registry（nn 是组装链叶子）。尺寸由上层 process factory 算出传入。
 */
public class BnnNnFactory implements NnFactory {
    @Override
    public INeuralNetwork create(int inputSize, int outputSize) {
        return new BnnNeuralNetwork(inputSize, outputSize);
    }
}
