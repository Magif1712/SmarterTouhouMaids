package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.value;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;

/**
 * 一个纯粹的输出向量容器，属于NN（神经网络）世界。
 * <p>
 * 这个类只负责封装一个代表输出数据的 {@link BoolVector} 实例。
 * 它对内部数据的具体含义一无所知。
 * 数据的解释和布局由应用世界的“视图”类（如 OutputVectorView）负责。
 */
public class OutputVector implements AutoCloseable {

    private final BoolVector underlying;

    /**
     * 构造一个OutputVector，它会创建并拥有其底层的BoolVector。
     *
     * @param size 底层BoolVector的总大小。
     */
    public OutputVector(int size) {
        this.underlying = new BoolVector(size);
    }

    public OutputVector(BoolVector vector) {
        this.underlying = vector;
    }

    /**
     * 获取底层的设备数组。
     * @return 代表整个输出向量的BoolVector实例。
     */
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