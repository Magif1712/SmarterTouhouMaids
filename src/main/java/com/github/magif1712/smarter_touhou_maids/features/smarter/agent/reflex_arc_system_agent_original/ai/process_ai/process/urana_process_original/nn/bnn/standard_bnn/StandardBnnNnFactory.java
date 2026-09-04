package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.standard_bnn;

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
 * standard_bnn 的 {@link NnFactory} 实现：叶子工厂，创建带输入变化门控重连的 bnn 网络。
 * <p>
 * 不查下层 registry（nn 是组装链叶子）。尺寸由上层 process factory 算出传入。
 * <p>
 * 设计原则（真善美第3条）：把"可选门控重连 bnn"这个不实在的约束，实在化为工厂对象。
 * 注册到 NnRegistry 后，GUI 可选择 standard_bnn 模式，urana 零改动。
 * <p>
 * <b>持久化声明</b>：实现 {@link PersistableProvider} 声明 NN 权重可持久化（b/p/q/l/r）。
 * 与 UranaProcessFactory 的 ∇C/继承声明叠加，路径任一声明 true 即默认开持久化。
 * <p>
 * <b>load + fallback</b>（C3 时机对称）：create 时先尝试从 slot.layerPath("nn") load 已有权重；
 * 目录无 b.bin（首次启动）或 load 异常（存档损坏）时 fallback 随机初始化。
 */
public class StandardBnnNnFactory implements NnFactory, PersistableProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger("StandardBnnNnFactory");

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
                    return StandardBnnNeuralNetwork.loadFromFile(nnPath);
                } catch (Exception e) {
                    LOGGER.warn("[StandardBnn] loadFromFile 失败，回退随机初始化: {}", e.getMessage(), e);
                }
            }
        }
        return new StandardBnnNeuralNetwork(inputSize, outputSize);
    }

    @Override
    public boolean hasPersistableData() {
        return true;
    }
}
