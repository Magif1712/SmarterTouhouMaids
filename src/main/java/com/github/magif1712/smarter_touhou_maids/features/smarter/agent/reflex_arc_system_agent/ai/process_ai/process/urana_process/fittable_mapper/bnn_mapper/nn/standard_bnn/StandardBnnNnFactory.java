package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.bnn_mapper.nn.standard_bnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.PersistableProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnEncodingProfile;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.AbstractBnnNeuralNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * standard_bnn（惊跳反射）的 {@link NnFactory} 实现：叶子工厂，创建带输入变化门控重连的 bnn 网络。
 * <p>
 * 不查下层 registry（nn 是组装链叶子）。尺寸由上层组装链算出传入（{@code FittableMapperFactory}
 * 的 default 组装：nnProvider.encodingProfile() → IODomain → totalLength）。
 * <p>
 * <b>持久化声明</b>：实现 {@link PersistableProvider} 声明 NN 权重可持久化（b/p/q/l/r）。
 * 与 UranaProcessFactory 的 ∇C/继承声明叠加，路径任一声明 true 即默认开持久化。
 * <p>
 * <b>load + fallback</b>（与 bnn 的 {@code BnnNnFactory} 对称，C3 时机）：create 时先尝试从
 * slot.layerPath("nn") load 已有权重；目录无 b_original.bin（首次启动）或 load 异常（存档损坏）
 * 时 fallback 随机初始化。权重文件与 bnn（original_bnn）<b>同款同名</b>（b_original.bin），
 * 与旧版 standard_bnn 工厂一致 ⇒ 两种 nn 共享存档权重，切换不丢已学权重。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第3条</b>：上层经 NN 插槽取 NnFactory 提供者，不依赖本类；删换本模块时上层零改动。</li>
 *   <li><b>第4条</b>：把「可持久化 + load fallback」这个不实在意图，实在化为 create 方法体。</li>
 * </ul>
 */
public class StandardBnnNnFactory implements NnFactory, PersistableProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger("StandardBnnNnFactory");

    @Override
    public NnEncodingProfile encodingProfile() {
        return AbstractBnnNeuralNetwork.BNN_PROFILE;
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
