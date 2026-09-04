package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.original_bnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.PersistableProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.NnEncodingProfile;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.AbstractBnnNeuralNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * BNN 的 {@link NnFactory} 实现：叶子工厂，直接 new BnnNeuralNetwork。
 * <p>
 * 不查下层 registry（nn 是组装链叶子）。尺寸由上层 process factory 算出传入。
 * <p>
 * <b>持久化声明</b>：实现 {@link PersistableProvider} 声明 NN 权重可持久化（b/p/q/l/r）。
 * <p>
 * <b>load + fallback</b>（C3 时机对称）：create 时先尝试从 slot load 已有权重；
 * 目录无 b.bin 或 load 异常时 fallback 随机初始化。
 */
public class BnnNnFactory implements NnFactory, PersistableProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger("BnnNnFactory");

    @Override
    public NnEncodingProfile encodingProfile() {
        return AbstractBnnNeuralNetwork.BNN_PROFILE_original;
    }

    @Override
    public INeuralNetwork create(SaveSlot slot, int inputSize, int outputSize) {
        if (slot != null) {
            String nnPath = slot.layerPath("nn");
            if (new File(nnPath, "b_original.bin").exists()) {
                try {
                    return BnnNeuralNetwork.loadFromFile(nnPath);
                } catch (Exception e) {
                    LOGGER.warn("[Bnn] loadFromFile 失败，回退随机初始化: {}", e.getMessage(), e);
                }
            }
        }
        return new BnnNeuralNetwork(inputSize, outputSize);
    }

    @Override
    public boolean hasPersistableData() {
        return true;
    }
}
