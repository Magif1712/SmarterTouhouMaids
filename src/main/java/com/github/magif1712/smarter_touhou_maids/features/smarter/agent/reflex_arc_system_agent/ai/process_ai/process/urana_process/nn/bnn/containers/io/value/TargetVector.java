package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.value;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;

/**
 * 目标向量容器，在训练或计算损失时使用。
 */
public class TargetVector implements AutoCloseable {

    private final BoolVector underlying;

    public TargetVector(int size) {
        this.underlying = new BoolVector(size);
    }

    public BoolVector getVector() {
        return underlying;
    }

    @Override
    public void close() {
        if (underlying != null) {
            underlying.close();
        }
    }
}