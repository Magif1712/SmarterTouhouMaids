package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.io.gradient;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.IntVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.mapping.training.BnnGradientProcessor;

import java.util.Objects;

/**
 * 代表输出层（δ_L）的梯度。
 * 这个类现在是一个纯粹的数据容器，用于封装一个已经计算好的梯度向量。
 * 它的创建由 {@link BnnGradientProcessor} 负责。
 */
public class BnnOutputLayerGradient implements AutoCloseable {
    private final IntVector gradient_original;

    /**
     * 构造一个输出层梯度容器.
     * <p>
     * 注意：这个构造函数是公共的，以便 {@link BnnGradientProcessor} 可以创建实例。
     * 在实践中，应始终通过 {@code BnnGradientProcessor} 来获取此类的实例。
     *
     * @param computedGradient 一个已经计算完成的梯度向量.
     */
    public BnnOutputLayerGradient(IntVector computedGradient) {
        this.gradient_original = Objects.requireNonNull(computedGradient, "计算好的梯度数组不能为空。");
    }

    /**
     * 获取持有计算后梯度的设备数组.
     *
     * @return 梯度向量.
     */
    public IntVector getVector() {
        return this.gradient_original;
    }

    /**
     * 释放梯度向量占用的 GPU 资源。
     * 由于该类创建并“拥有”梯度，因此它负责释放梯度。
     */
    @Override
    public void close() {
        this.gradient_original.close();
    }
}
