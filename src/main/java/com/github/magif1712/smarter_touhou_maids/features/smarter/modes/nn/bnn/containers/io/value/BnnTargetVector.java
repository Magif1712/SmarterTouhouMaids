package com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.bnn.containers.io.value;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;

/**
 * 目标向量容器，在训练或计算损失时使用。
 */
public class BnnTargetVector implements AutoCloseable {

    private final BoolVector underlying_original;

    public BnnTargetVector(int size) {
        this.underlying_original = new BoolVector(size);
    }

    public BoolVector getVector() {
        return underlying_original;
    }

    @Override
    public void close() {
        if (underlying_original != null) {
            underlying_original.close();
        }
    }
}
