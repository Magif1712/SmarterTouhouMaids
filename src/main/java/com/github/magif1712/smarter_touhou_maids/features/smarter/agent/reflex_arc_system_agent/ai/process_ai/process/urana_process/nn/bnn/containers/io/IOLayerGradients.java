package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.IntVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.gradient.InputLayerGradient;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.gradient.OutputLayerGradient;

/**
 * 一个容器类，用于封装和管理输入层与输出层的梯度。
 * <p>
 * 这个类的设计模式与 {@link IO} 类似，旨在将反向传播中概念上关联的数据（输入层梯度和输出层梯度）
 * 捆绑成一个逻辑单元，以简化接口和统一生命周期管理。
 * <p>
 * 设计原则（真善美第2条）：原构造反向 import urana 的 InputVectorDomain/OutputVectorDomain
 * 取 TOTAL_LENGTH 定尺寸，导致下层 nn 知道上层 urana 的 span 语义——方向反了。
 * 改为构造参数化，由调用方（BnnNeuralNetwork）传入尺寸，nn 不再依赖 urana。
 */
public class IOLayerGradients implements AutoCloseable {

    private final InputLayerGradient inputLayerGradient;
    private final OutputLayerGradient outputLayerGradient;
    /**
     * 反向传播中间临时工作缓冲区 dz（与输出层同尺寸 sizeA1 = n_curr）。
     * <p>
     * 设计原则（真善美）：
     * 用预分配的实在缓冲区替代每次 cudaMallocAsync 的瞬时分配（~796 MB）。
     * 3 个串行 GradCell 共享同一份 IOLayerGradients（含此 dzWorkspace），
     * 消除内存池残留与瞬时峰值。
     */
    private final IntVector dzWorkspace;

    /**
     * 构造一个新的 IOLayerGradients 实例。
     *
     * @param inputSize  输入层梯度向量的尺寸（由调用方传入，nn 不再依赖 urana 的 InputVectorDomain）。
     * @param outputSize 输出层梯度向量及 dzWorkspace 的尺寸。
     */
    public IOLayerGradients(int inputSize, int outputSize) {
        this.inputLayerGradient = new InputLayerGradient(new IntVector(inputSize));
        this.outputLayerGradient = new OutputLayerGradient(new IntVector(outputSize));
        this.dzWorkspace = new IntVector(outputSize);
    }

    public InputLayerGradient getInputLayerGradient() {
        return inputLayerGradient;
    }

    public OutputLayerGradient getOutputLayerGradient() {
        return outputLayerGradient;
    }

    /**
     * 获取反向传播中间工作缓冲区 dz。
     * 调用方不应假设其内容——每次 backward 都会被 gradientSignFlip 完全覆盖。
     */
    public IntVector getDzWorkspace() {
        return dzWorkspace;
    }

    @Override
    public void close() throws Exception {
        if (inputLayerGradient != null) {
            inputLayerGradient.close();
        }
        if (outputLayerGradient != null) {
            outputLayerGradient.close();
        }
        if (dzWorkspace != null) {
            dzWorkspace.close();
        }
    }
}