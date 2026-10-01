package com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.cnn.containers.io.gradient;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;

/**
 * CNN 输入层梯度：包一个 {@link FloatVector}。{@code close()} 真实释放 underlying。
 */
public class CnnInputLayerGradient implements AutoCloseable {
    private final FloatVector underlying;

    public CnnInputLayerGradient(FloatVector underlying) {
        this.underlying = underlying;
    }

    public FloatVector getVector() {
        return underlying;
    }

    @Override
    public void close() {
        underlying.close();
    }
}