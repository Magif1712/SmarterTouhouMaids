package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.value.InputVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.value.OutputVector;

/**
 * IO（输入/输出）容器。
 */
public class IO implements AutoCloseable {
    private final InputVector input;
    private final OutputVector output;

    /**
     * 构造一个IO容器，并自动创建内部的InputVector和OutputVector。
     *
     * @param sizeA0 输入向量的大小。
     * @param sizeA1 输出向量的大小。
     */
    public IO(int sizeA0, int sizeA1) {
        this.input = new InputVector(sizeA0);
        this.output = new OutputVector(sizeA1);
    }

    public InputVector getInput() {
        return input;
    }

    public OutputVector getOutput() {
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