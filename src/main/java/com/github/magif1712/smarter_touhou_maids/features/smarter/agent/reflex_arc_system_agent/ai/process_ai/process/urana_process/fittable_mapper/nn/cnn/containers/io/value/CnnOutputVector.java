package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn.containers.io.value;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;

/**
 * CNN 输出向量：双构造——{@code int} 时新建 {@link FloatVector}，{@code FloatVector} 时直接包。
 * {@code close()} 真实释放 underlying。
 */
public class CnnOutputVector implements AutoCloseable {
    private final FloatVector underlying;

    public CnnOutputVector(int size) {
        this.underlying = new FloatVector(size);
    }

    public CnnOutputVector(FloatVector vector) {
        this.underlying = vector;
    }

    public FloatVector getVector() {
        return underlying;
    }

    @Override
    public void close() {
        underlying.close();
    }
}