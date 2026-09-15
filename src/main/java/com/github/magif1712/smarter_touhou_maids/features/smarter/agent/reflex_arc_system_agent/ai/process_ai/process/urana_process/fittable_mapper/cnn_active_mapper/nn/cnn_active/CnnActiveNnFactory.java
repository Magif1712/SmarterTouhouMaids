package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper.nn.cnn_active;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.PersistableProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnEncodingProfile;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.cnn.AbstractCnnNeuralNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 新活性 CNN 的 NN 工厂（叶子工厂）：{@code encodingProfile()} 返回
 * {@link AbstractCnnNeuralNetwork#CNN_PROFILE}（定理1(3) 的活动域契约），
 * {@code create(...)} 先尝试从 slot load 已有权重，目录无文件或 load 异常时 fallback 对称初始化。
 * <p>
 * <b>独立层目录键</b>（方案 §九）：权重写在 {@code <layerPath("cnn_active")>/p.bin...}，
 * <b>不读</b>原 CNN 的 {@code nn/p.bin}——两者的符号域（对称 vs 单边非负）与激活（奇 vs sigmoid）
 * 都不同，混用会让行为怪异且无法定位。
 * <p>
 * <b>持久化声明</b>：实现 {@link PersistableProvider} 声明 NN 权重可持久化（p/q/l/r/b）。
 * 与 {@code CnnNnFactory} 对称：load + fallback 模式、声明、LOGGER 告警均同构（照抄 + 改目录键与 D）。
 */
public class CnnActiveNnFactory implements NnFactory, PersistableProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger("CnnActiveNnFactory");

    /**
     * 本 NN 的独立层目录键（{@link SaveSlot#layerPath(String)} 的参数）。
     * <p>
     * 与原 CNN 的 {@code "nn"} 刻意不同——这是"新旧权重不混用"这一风险对策的实在化（方案 §十一）。
     */
    public static final String NN_LAYER_ID = "cnn_active";

    @Override
    public NnEncodingProfile encodingProfile() {
        return AbstractCnnNeuralNetwork.CNN_PROFILE;
    }

    @Override
    public INeuralNetwork create(SaveSlot slot, int inputSize, int outputSize) {
        // 无 D 的默认路径（理论不发生：cnn_active 只由 cnn_active_mapper 经 createConfigured 实例化）。
        return create(slot, inputSize, outputSize, CnnActiveOptions.defaults());
    }

    /**
     * 带分解方式 D 的实例化：先 load（保留磁盘符号域），否则全新对称初始化。
     *
     * @param options 本次选择的分解方式 D（在上层 GUI 选定，由 {@code CnnActiveMapperFactory} 传入）
     */
    public INeuralNetwork create(SaveSlot slot, int inputSize, int outputSize, CnnActiveOptions options) {
        if (slot != null) {
            String nnPath = slot.layerPath(NN_LAYER_ID);
            if (new File(nnPath, "p.bin").exists()) {
                try {
                    return CnnActiveNeuralNetwork.loadFromFile(nnPath, options);
                } catch (Exception e) {
                    LOGGER.warn("[CnnActive] loadFromFile 失败，回退对称初始化: {}", e.getMessage(), e);
                }
            }
        }
        return CnnActiveNeuralNetwork.createFresh(inputSize, outputSize, options);
    }

    /**
     * 把"任意 {@link NnFactory} 提供者 + 本模块的 D"收拢到一处受控转换，供
     * {@code CnnActiveMapperFactory} 调用。
     * <p>
     * <b>为什么可以安全转换</b>：{@code cnn_active_mapper} 的 nn 子插槽由
     * {@code CnnActiveMapperModes} <b>自声明</b>（{@code branch.addChild("nn", 自己的插槽键)}），
     * 而该插槽只注册 cnn_active 一个 Branch ⟹ "本 mapper 的 nn 必然是 cnn_active"是<b>结构保证</b>，
     * 与 {@code AssemblyContext.child(name, type)} 的受控强转同理。
     * <p>
     * 若不是（结构性破坏），fail-fast 而非静默降级——静默会让错误的 nn 拿到本模块的 ops，症状隐蔽。
     */
    public static INeuralNetwork createConfigured(NnFactory provider, SaveSlot slot, int inputSize, int outputSize, CnnActiveOptions options) {
        if (provider instanceof CnnActiveNnFactory active) {
            return active.create(slot, inputSize, outputSize, options);
        }
        throw new IllegalStateException(
                "cnn_active_mapper 的 nn 子插槽只应含 cnn_active，实际为: " + provider.getClass().getName());
    }

    @Override
    public boolean hasPersistableData() {
        return true;
    }
}
