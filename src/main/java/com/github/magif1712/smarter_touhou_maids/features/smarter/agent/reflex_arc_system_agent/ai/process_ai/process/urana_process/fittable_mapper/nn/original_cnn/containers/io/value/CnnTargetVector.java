package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.containers.io.value;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;

/**
 * CNN 目标向量：训练目标载体。{@code close()} 真实释放 underlying。
 */
public class CnnTargetVector implements AutoCloseable {
    private final FloatVector underlying;

    public CnnTargetVector(int size) {
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
