package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_bnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.PersistableProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.NnEncodingProfile;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.NnFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * BNN 的 {@link NnFactory} 实现（叶子工厂，新版架构）。
 * <p>
 * 不查下层 registry（nn 是组装链叶子）。尺寸由上层 process factory 算出传入。
 * <p>
 * <b>持久化声明</b>：实现 {@link PersistableProvider} 声明 NN 权重可持久化（b/p/q/l/r）。
 * <p>
 * <b>load + fallback</b>（与原初代理 {@code BnnNnFactory} 对称）：create 时先尝试从 slot
 * load 已有权重（{@code b_original.bin}，与原初代理同款文件名以保证权重互通）；
 * 目录无文件或 load 异常时 fallback 随机初始化。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第3条</b>：上层 process 经 NnRegistry 取 NnFactory，不依赖本类；删换本模块时上层零改动。</li>
 *   <li><b>第4条</b>：把"可持久化 + load fallback"这个不实在意图，实在化为 create 方法体。</li>
 * </ul>
 */
public class BnnNnFactory implements NnFactory, PersistableProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger("BnnNnFactory");

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
