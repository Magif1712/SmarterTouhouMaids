package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.containers.io;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.containers.io.value.CnnInputVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.containers.io.value.CnnOutputVector;

/**
 * CNN IO：把"输入向量 + 输出向量"这个不实在对偶，实在化为一个对象（真善美第4条）。
 * {@code getA0/getA1} 直接返回 underlying {@link FloatVector} 供 ops 桥接层提取句柄。
 * {@code close()} 释放 input + output。
 */
public class CnnIO implements AutoCloseable {
    private final CnnInputVector input;
    private final CnnOutputVector output;

    public CnnIO(int sizeA0, int sizeA1) {
        this.input = new CnnInputVector(sizeA0);
        this.output = new CnnOutputVector(sizeA1);
    }

    public CnnInputVector getInput() {
        return input;
    }

    public CnnOutputVector getOutput() {
        return output;
    }

    public FloatVector getA0() {
        return input.getVector();
    }

    public FloatVector getA1() {
        return output.getVector();
    }

    @Override
    public void close() {
        input.close();
        output.close();
    }
}
