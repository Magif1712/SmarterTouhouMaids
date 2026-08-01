package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.IntVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.NetworkData;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.IO;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.IOLayerGradients;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.value.OutputVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.value.TargetVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.mapping.BnnOps;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.mapping.NetworkProcessor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.mapping.training.GradientProcessor;

/**
 * BNN（二值神经网络）的 {@link INeuralNetwork} 实现。
 * <p>
 * 聚合 BNN 的全部资源（{@link NetworkData}/{@link IO}/{@link IOLayerGradients}/{@link TargetVector}）
 * 与操作（{@link NetworkProcessor}/{@link GradientProcessor}），对外只暴露机械级接口。
 * urana 通过 {@link INeuralNetwork} 接口访问，不知道 BNN 的权重结构、载体类型（BoolVector/IntVector）、
 * forward/backward 的 native 细节。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：BNN 的所有具体模式（b/p/q/l/r 权重、BoolVector bit 载体、forwardNoFz、
 *       两阶段 BPTT 的中间态等）藏在实现里；urana 不感知，只调接口方法。</li>
 *   <li><b>第3条</b>：把"可换 NN"这个不实在约束，实在化为 BnnNeuralNetwork/CnnNeuralNetwork 等实现类。</li>
 * </ul>
 * <p>
 * <b>target 归属</b>：BnnNeuralNetwork 内部持 target（{@link #setTarget} 填它，
 * {@link #computeOutputGradient} 直接用），urana 不持有 target——target 是 nn 训练资源，
 * 不是意识体状态。这与"∇C/fz/ChainStepState 留 urana"不矛盾：∇C 是链式训练跨步状态（urana 模式），
 * target 是 nn 自有资源（nn 模式）。
 */
public class BnnNeuralNetwork implements INeuralNetwork {

    private final NetworkData networkData;
    private final IO io;
    private final IOLayerGradients gradients;
    private final TargetVector target;
    private final int inputSize;
    private final int outputSize;

    /**
     * 新建 BNN 网络（随机初始化权重）。
     *
     * @param inputSize  输入向量尺寸（由 urana 传入，BNN 不再反向 import urana 的 InputVectorDomain）。
     * @param outputSize 输出向量尺寸。
     */
    public BnnNeuralNetwork(int inputSize, int outputSize) {
        this.inputSize = inputSize;
        this.outputSize = outputSize;
        try {
            this.networkData = new NetworkData(inputSize, outputSize, true);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to initialize BNN NetworkData", e);
        }
        this.io = new IO(inputSize, outputSize);
        this.gradients = new IOLayerGradients(inputSize, outputSize);
        this.target = new TargetVector(outputSize);
    }

    private BnnNeuralNetwork(NetworkData networkData, int inputSize, int outputSize) {
        this.networkData = networkData;
        this.inputSize = inputSize;
        this.outputSize = outputSize;
        this.io = new IO(inputSize, outputSize);
        this.gradients = new IOLayerGradients(inputSize, outputSize);
        this.target = new TargetVector(outputSize);
    }

    /**
     * 从磁盘加载 BNN 权重，自动反推尺寸。
     */
    public static BnnNeuralNetwork loadFromFile(String folderPath) {
        NetworkData net = NetworkData.loadFromFile(folderPath);
        int in = net.getHyperparameters().getSizeA0();
        int out = net.getHyperparameters().getSizeA1();
        return new BnnNeuralNetwork(net, in, out);
    }

    // ==================== IO 区域读写 ====================

    @Override
    public void copyToInput(Span region, VectorBase src, long stream) {
        BoolVector inputVec = io.getInput().getVector();
        inputVec.setRegion(region, (BoolVector) src, stream);
    }

    @Override
    public void copyFromInput(Span region, VectorBase dst, long stream) {
        BoolVector inputVec = io.getInput().getVector();
        dst.copyRegionFrom(inputVec, region, fullSpan(dst), stream);
    }

    @Override
    public void copyFromOutput(Span region, VectorBase dst, long stream) {
        BoolVector outputVec = io.getOutput().getVector();
        dst.copyRegionFrom(outputVec, region, fullSpan(dst), stream);
    }

    @Override
    public void copyToInputFromHost(Span region, boolean[] src, long stream) {
        BoolVector inputVec = io.getInput().getVector();
        inputVec.copyRegionFromHost(region, src, stream);
    }

    /**
     * BNN 特定编码：long 拆成 2 个 uint32 word，走 BoolVector.copyFromHost 的 word 级直通路径。
     * <p>
     * dt 语义在 urana（urana 传 dtSpan + dtMillis）；本方法只做"long→bit 载体→填 region"的机械动作。
     */
    @Override
    public void copyToInputFromLong(Span region, long value, long stream) {
        int[] packed = new int[2];
        packed[0] = (int) (value & 0xFFFFFFFFL);
        packed[1] = (int) ((value >>> 32) & 0xFFFFFFFFL);
        BoolVector inputVec = io.getInput().getVector();
        // dtSpan.length 必须是 64 bit（2 word）。直接走 copyFromHost 到对应 bit 偏移。
        // BoolVector.copyFromHost 是从向量起点写——这里 dtSpan 可能在 input 中间，需要走 setRegion 等价路径。
        // 实测 dtSpan 固定 64 bit，用临时 host buffer + copyRegionFromHost 写入。
        // 但 boolean[] 走 copyRegionFromHost 也是 host→device，与原 encodeDt 等价。
        boolean[] bits = longToBoolArray(value);
        inputVec.copyRegionFromHost(region, bits, stream);
    }

    /**
     * 把 long 拆成 64 个 boolean（BNN bit 编码）。
     */
    private static boolean[] longToBoolArray(long value) {
        boolean[] bits = new boolean[64];
        for (int i = 0; i < 64; i++) {
            bits[i] = ((value >>> i) & 1L) != 0;
        }
        return bits;
    }

    // ==================== 前向 / 反向 ====================

    @Override
    public void forward(long stream) {
        NetworkProcessor.forwardNoFz(networkData, io, stream);
    }

    @Override
    public void forwardForTraining(VectorBase fz, long stream) {
        NetworkProcessor.forwardStoreFz(networkData, io, (BoolVector) fz, stream);
    }

    @Override
    public void backward(VectorBase fz, long stream) {
        try {
            NetworkProcessor.backward(networkData, gradients, (BoolVector) fz, stream);
        } catch (Exception e) {
            throw new RuntimeException("BNN backward failed", e);
        }
    }

    @Override
    public void backwardAndUpdate(VectorBase fz, VectorBase aPrev, long stream) {
        try {
            NetworkProcessor.backwardWithGradientDescent(
                    networkData, gradients, (BoolVector) fz, (BoolVector) aPrev, stream);
        } catch (Exception e) {
            throw new RuntimeException("BNN backwardAndUpdate failed", e);
        }
    }

    // ==================== 目标与梯度 ====================

    @Override
    public void setTarget(Span region, VectorBase src, long stream) {
        target.getVector().setRegion(region, (BoolVector) src, stream);
    }

    @Override
    public void computeOutputGradient(VectorBase currentOutput, Span region, long stream) {
        // BNN 梯度计算：GradientProcessor 签名要求 OutputVector 包装。
        OutputVector cur = new OutputVector((BoolVector) currentOutput);
        GradientProcessor.calculateOutputLayerGradient(
                cur, target, gradients.getOutputLayerGradient(), region, stream);
    }

    @Override
    public void injectOutputGradient(Span region, VectorBase gradC, long stream) {
        IntVector outputGradVec = gradients.getOutputLayerGradient().getVector();
        outputGradVec.setRegion(region, (IntVector) gradC, stream);
    }

    @Override
    public void copyFromInputGradient(Span region, VectorBase dst, long stream) {
        IntVector inputGradVec = gradients.getInputLayerGradient().getVector();
        dst.copyRegionFrom(inputGradVec, region, fullSpan(dst), stream);
    }

    /**
     * BNN 特定：negateAndBinarize（IntVector 梯度 → BoolVector 输入）。
     */
    @Override
    public void gradientToInput(VectorBase gradC, VectorBase inputC, long stream) {
        BnnOps.negateAndBinarize((IntVector) gradC, (BoolVector) inputC, stream);
    }

    /**
     * BNN 特定：IntVector.multiplyByScalar(0)。
     */
    @Override
    public void zeroGradient(VectorBase gradVec, long stream) {
        ((IntVector) gradVec).multiplyByScalar(0, stream);
    }

    /**
     * BNN 特定：BoolVector 用 copyRegionFromHost(false[])。
     */
    @Override
    public void zeroVector(VectorBase vec, long stream) {
        BoolVector bv = (BoolVector) vec;
        bv.copyRegionFromHost(fullSpan(bv), new boolean[bv.size()], stream);
    }

    // ==================== 向量工厂 ====================

    @Override
    public VectorBase createVector(int size) {
        return new BoolVector(size);
    }

    @Override
    public VectorBase createGradientVector(int size) {
        return new IntVector(size);
    }

    // ==================== 序列化 ====================

    @Override
    public void save(String folderPath) {
        networkData.save(folderPath);
    }

    @Override
    public void close() throws Exception {
        if (target != null) target.close();
        if (gradients != null) gradients.close();
        if (io != null) io.close();
        if (networkData != null) networkData.close();
    }

    private static Span fullSpan(VectorBase v) {
        return new Span(0, v.size()) {
        };
    }
}
