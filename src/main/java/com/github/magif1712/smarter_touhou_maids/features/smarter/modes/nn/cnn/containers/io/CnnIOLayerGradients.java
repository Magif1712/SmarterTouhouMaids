package com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.cnn.containers.io;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.cnn.containers.io.gradient.CnnInputLayerGradient;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.cnn.containers.io.gradient.CnnOutputLayerGradient;

/**
 * CNN IO 层梯度：把"输入层梯度 + 输出层梯度 + dz 工作区"这个不实在的三元组，实在化为一个对象（真善美第4条）。
 * {@code dzWorkspace} 是反向的中间态工作区（输出尺寸）。{@code close()} 释放三者。
 */
public class CnnIOLayerGradients implements AutoCloseable {
    private final CnnInputLayerGradient inputLayerGradient;
    private final CnnOutputLayerGradient outputLayerGradient;
    private final FloatVector dzWorkspace;

    public CnnIOLayerGradients(int sizeA0, int sizeA1) {
        this.inputLayerGradient = new CnnInputLayerGradient(new FloatVector(sizeA0));
        this.outputLayerGradient = new CnnOutputLayerGradient(new FloatVector(sizeA1));
        this.dzWorkspace = new FloatVector(sizeA1);
    }

    public CnnInputLayerGradient getInputLayerGradient() {
        return inputLayerGradient;
    }

    public CnnOutputLayerGradient getOutputLayerGradient() {
        return outputLayerGradient;
    }

    public FloatVector getDzWorkspace() {
        return dzWorkspace;
    }

    @Override
    public void close() {
        inputLayerGradient.close();
        outputLayerGradient.close();
        dzWorkspace.close();
    }
}