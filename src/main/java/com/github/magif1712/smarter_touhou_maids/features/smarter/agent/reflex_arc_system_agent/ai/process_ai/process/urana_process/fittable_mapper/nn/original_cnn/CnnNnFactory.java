package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.NnEncodingProfile;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.AbstractCnnNeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.INeuralNetwork;

/**
 * 朴素 CNN 的 NN 工厂（叶子工厂）：{@code encodingProfile()} 返回 {@link AbstractCnnNeuralNetwork#CNN_PROFILE}，
 * {@code create(...)} 忽略 slot 直接新建 {@link CnnNeuralNetwork}（照搬伪代码，无 load+fallback）。
 */
public class CnnNnFactory implements NnFactory {

    @Override
    public NnEncodingProfile encodingProfile() {
        return AbstractCnnNeuralNetwork.CNN_PROFILE;
    }

    @Override
    public INeuralNetwork create(SaveSlot slot, int inputSize, int outputSize) {
        return new CnnNeuralNetwork(inputSize, outputSize);
    }
}
