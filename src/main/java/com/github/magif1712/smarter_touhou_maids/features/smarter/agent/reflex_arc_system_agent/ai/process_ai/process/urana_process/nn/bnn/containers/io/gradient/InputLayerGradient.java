package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.gradient;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.IntVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.value.InputVector;

import java.util.Objects;

/**
 * 一个纯粹的输入层梯度容器，属于NN（神经网络）世界。
 * <p>
 * 这个类遵循“数据与梯度分离”的设计原则，与 {@link InputVector} 分离。
 * 它只负责封装一个代表输入层梯度的 {@link IntVector} 实例。
 */
public class InputLayerGradient implements AutoCloseable {

    private final IntVector underlying;

    /**
     * 构造一个InputLayerGradient，它会接收并拥有其底层的IntVector。
     *
     * @param underlyingGradient 一个代表输入层梯度的IntVector实例。不能为空。
     */
    public InputLayerGradient(IntVector underlyingGradient) {
        this.underlying = Objects.requireNonNull(underlyingGradient, "底层的梯度数组不能为空。");
    }

    /**
     * 获取底层的设备数组。
     * @return 代表整个输入层梯度的IntVector实例。
     */
    public IntVector getVector() {
        return underlying;
    }

    @Override
    public void close() {
        if (underlying != null) {
            underlying.close();
        }
    }
}