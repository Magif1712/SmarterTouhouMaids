package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.containers.io.gradient;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;

/**
 * CNN 输出层梯度：包一个 {@link FloatVector}。{@code close()} 真实释放 underlying。
 */
public class CnnOutputLayerGradient implements AutoCloseable {
    private final FloatVector gradient;

    public CnnOutputLayerGradient(FloatVector gradient) {
        this.gradient = gradient;
    }

    public FloatVector getVector() {
        return gradient;
    }

    @Override
    public void close() {
        gradient.close();
    }
}
