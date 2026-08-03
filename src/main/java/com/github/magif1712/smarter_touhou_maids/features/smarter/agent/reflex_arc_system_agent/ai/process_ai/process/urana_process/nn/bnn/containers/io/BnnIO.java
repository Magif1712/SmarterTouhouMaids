package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.value.BnnInputVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.value.BnnOutputVector;

/**
 * BnnIO（输入/输出）容器。
 */
public class BnnIO implements AutoCloseable {
    private final BnnInputVector input;
    private final BnnOutputVector output;

    /**
     * 构造一个IO容器，并自动创建内部的InputVector和OutputVector。
     *
     * @param sizeA0 输入向量的大小。
     * @param sizeA1 输出向量的大小。
     */
    public BnnIO(int sizeA0, int sizeA1) {
        this.input = new BnnInputVector(sizeA0);
        this.output = new BnnOutputVector(sizeA1);
    }

    public BnnInputVector getInput() {
        return input;
    }

    public BnnOutputVector getOutput() {
        return output;
    }

    public BoolVector getA0() {
        return input.getVector();
    }

    public BoolVector getA1() {
        return output.getVector();
    }

    @Override
    public void close() throws Exception {
        input.close();
        output.close();
    }
}