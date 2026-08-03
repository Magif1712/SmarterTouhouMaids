package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.value;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;

/**
 * 一个纯粹的输入向量容器，属于NN（神经网络）世界。
 * <p>
 * 这个类只负责封装一个代表输入数据的 {@link BoolVector} 实例。
 * 它对内部数据的具体含义（如“感觉”、“继承信息”等）一无所知。
 * 数据的解释和布局由应用世界的“视图”类（如 InputVectorView）负责。
 */
public class BnnInputVector implements AutoCloseable {

    private final BoolVector underlying;

    /**
     * 构造一个InputVector，它会创建并拥有其底层的BoolVector。
     *
     * @param size 底层BoolVector的总大小。
     */
    public BnnInputVector(int size) {
        this.underlying = new BoolVector(size);
    }

    /**
     * 获取底层的布尔向量。
     * @return 代表整个输入向量的BoolVector实例。
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