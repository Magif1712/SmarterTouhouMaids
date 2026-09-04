package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.io;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.io.value.BnnInputVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.io.value.BnnOutputVector;

/**
 * BnnIO（输入/输出）容器。
 */
public class BnnIO implements AutoCloseable {
    private final BnnInputVector input_original;
    private final BnnOutputVector output_original;

    /**
     * 构造一个IO容器，并自动创建内部的InputVector和OutputVector。
     *
     * @param sizeA0 输入向量的大小。
     * @param sizeA1 输出向量的大小。
     */
    public BnnIO(int sizeA0, int sizeA1) {
        this.input_original = new BnnInputVector(sizeA0);
        this.output_original = new BnnOutputVector(sizeA1);
    }

    public BnnInputVector getInput() {
        return input_original;
    }

    public BnnOutputVector getOutput() {
        return output_original;
    }

    public BoolVector getA0() {
        return input_original.getVector();
    }

    public BoolVector getA1() {
        return output_original.getVector();
    }

    @Override
    public void close() throws Exception {
        input_original.close();
        output_original.close();
    }
}
