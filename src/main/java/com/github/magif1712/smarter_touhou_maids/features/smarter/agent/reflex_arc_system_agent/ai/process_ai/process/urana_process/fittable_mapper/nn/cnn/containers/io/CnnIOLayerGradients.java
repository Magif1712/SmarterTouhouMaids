package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn.containers.io;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn.containers.io.gradient.CnnInputLayerGradient;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn.containers.io.gradient.CnnOutputLayerGradient;

/**
 * CNN IO 层梯度：把"输入层梯度 + 输出层梯度 + dz 工作区"这个不实在的三元组，实在化为一个对象（真善美第4条）。
 * {@code dzWorkspace} 是反向的中间态工作区（输出尺寸）。{@code close()} 释放三者。
 */
public class CnnIOLayerGradients implements AutoCloseable {
    private final CnnInputLayerGradient inputLayerGradient;
    private final CnnOutputLayerGradient outputLayerGradient;
    private final FloatVector dzWorkspace;

    public CnnIOLayerGradients(int inputSize, int outputSize) {
        this.inputLayerGradient = new CnnInputLayerGradient(new FloatVector(inputSize));
        this.outputLayerGradient = new CnnOutputLayerGradient(new FloatVector(outputSize));
        this.dzWorkspace = new FloatVector(outputSize);
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