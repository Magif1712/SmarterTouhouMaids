package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.PersistableProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.NnEncodingProfile;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.AbstractCnnNeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.INeuralNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 朴素 CNN 的 NN 工厂（叶子工厂）：{@code encodingProfile()} 返回 {@link AbstractCnnNeuralNetwork#CNN_PROFILE}，
 * {@code create(...)} 先尝试从 slot load 已有权重（{@code p.bin}），目录无文件或 load 异常时 fallback 随机初始化。
 * <p>
 * <b>持久化声明</b>：实现 {@link PersistableProvider} 声明 NN 权重可持久化（p/q/l/r/b）。
 * <p>
 * 与 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_bnn.BnnNnFactory}
 * 对称：load + fallback 模式、PersistableProvider 声明、LOGGER 告警均同构。
 */
public class CnnNnFactory implements NnFactory, PersistableProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger("CnnNnFactory");

    @Override
    public NnEncodingProfile encodingProfile() {
        return AbstractCnnNeuralNetwork.CNN_PROFILE;
    }

    @Override
    public INeuralNetwork create(SaveSlot slot, int inputSize, int outputSize) {
        if (slot != null) {
            String nnPath = slot.layerPath("nn");
            if (new File(nnPath, "p.bin").exists()) {
                try {
                    return CnnNeuralNetwork.loadFromFile(nnPath);
                } catch (Exception e) {
                    LOGGER.warn("[Cnn] loadFromFile 失败，回退随机初始化: {}", e.getMessage(), e);
                }
            }
        }
        return new CnnNeuralNetwork(inputSize, outputSize);
    }

    @Override
    public boolean hasPersistableData() {
        return true;
    }
}