package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.containers.io.value;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;

/**
 * CNN 输入向量：包一个 {@link FloatVector}，提供 {@code getVector()} 透明访问 underlying。
 * {@code close()} 真实释放 underlying。
 */
public class CnnInputVector implements AutoCloseable {
    private final FloatVector underlying;

    public CnnInputVector(int size) {
        this.underlying = new FloatVector(size);
    }

    public FloatVector getVector() {
        return underlying;
    }

    @Override
    public void close() {
        underlying.close();
    }
}
