package com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.cnn.containers.io;

import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.cnn.containers.io.value.CnnInputVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.cnn.containers.io.value.CnnOutputVector;

/**
 * CNN IO：把"输入向量 + 输出向量"这个不实在对偶，实在化为一个对象（真善美第4条）。
 * ops 桥接层需 underlying {@link com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector} 句柄时，
 * 通过 {@code getInput().getVector()} / {@code getOutput().getVector()} 逐层获取（设计原则第2/3条：不跳层）。
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

    @Override
    public void close() {
        input.close();
        output.close();
    }
}