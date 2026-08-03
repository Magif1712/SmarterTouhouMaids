package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.IntVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.BnnNetworkData;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.BnnIO;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.BnnIOLayerGradients;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.value.BnnOutputVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.value.BnnTargetVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.mapping.BnnOps;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.mapping.BnnNetworkProcessor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.mapping.training.BnnGradientProcessor;

/**
 * bnn 家族的共享内核：把 bnn mechanics（{@link BnnNetworkData}/{@link BnnIO}/
 * {@link BnnIOLayerGradients}/{@link BnnTargetVector} 与 {@link BnnNetworkProcessor}/
 * {@link BnnGradientProcessor}）适配到 {@link INeuralNetwork} 机械级接口。
 * <p>
 * 本类是<b>家族核心，不是模式</b>：它住在 {@code nn.bnn} 包（家族层），不在任何具体叶子
 * （original_bnn / standard_bnn）里。两个叶子都是它的真兄弟子类，互不依赖——任一叶子可被删除
 * 而另一叶子与上层照常工作（真善美第3条：哪怕删除原模式、用新模式替换也不改上层代码）。
 * <p>
 * 子类按需 override {@link #forward}/{@link #forwardForTraining}/{@link #close} 注入额外行为
 * （如 standard_bnn 的输入变化门控重连）；其余 INeuralNetwork 方法由本类提供朴素 bnn 实现。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：bnn 的具体模式（b/p/q/l/r 权重、BoolVector/IntVector 载体、forwardNoFz、
 *       两阶段 BPTT 中间态等）藏在家族内核里；urana 只通过 {@link INeuralNetwork} 访问，不感知。</li>
 *   <li><b>第3条</b>：把"可换 nn"这个不实在约束实在化为接口 + 一族实现类；共享内核上提到家族层，
 *       使每个叶子模式可独立删换而不影响其他叶子与上层。</li>
 *   <li><b>第4条</b>：把"bnn 适配 INeuralNetwork"这个不实在的约束，实在化为本抽象类的方法签名。</li>
 * </ul>
 * <p>
 * <b>target 归属</b>：本类内部持 target（{@link #setTarget} 填它，{@link #computeOutputGradient}
 * 直接用），urana 不持有 target——target 是 nn 训练资源，不是意识体状态。
 */
public abstract class AbstractBnnNeuralNetwork implements INeuralNetwork {

    protected final BnnNetworkData networkData;
    protected final BnnIO io;
    protected final BnnIOLayerGradients gradients;
    protected final BnnTargetVector target;
    protected final int inputSize;
    protected final int outputSize;

    /**
     * 新建 bnn 网络（随机初始化权重）。
     *
     * @param inputSize  输入向量尺寸（由 urana 传入）。
     * @param outputSize 输出向量尺寸。
     */
    public AbstractBnnNeuralNetwork(int inputSize, int outputSize) {
        this.inputSize = inputSize;
        this.outputSize = outputSize;
        try {
            this.networkData = new BnnNetworkData(inputSize, outputSize, true);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to initialize BnnNetworkData", e);
        }
        this.io = new BnnIO(inputSize, outputSize);
        this.gradients = new BnnIOLayerGradients(inputSize, outputSize);
        this.target = new BnnTargetVector(outputSize);
    }

    /**
     * 从已加载权重构造（供叶子 {@code loadFromFile} 复用）。
     */
    protected AbstractBnnNeuralNetwork(BnnNetworkData networkData, int inputSize, int outputSize) {
        this.networkData = networkData;
        this.inputSize = inputSize;
        this.outputSize = outputSize;
        this.io = new BnnIO(inputSize, outputSize);
        this.gradients = new BnnIOLayerGradients(inputSize, outputSize);
        this.target = new BnnTargetVector(outputSize);
    }

    /**
     * 从磁盘加载 bnn 权重，自动反推尺寸（叶子 {@code loadFromFile} 共用）。
     */
    protected static BnnNetworkData loadNetworkData(String folderPath) {
        return BnnNetworkData.loadFromFile(folderPath);
    }

    // ==================== BnnIO 区域读写 ====================

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
     * BNN 特定编码：long 拆成 64 个 boolean，走 BoolVector.copyRegionFromHost 的 bit 级路径。
     * <p>
     * dt 语义在 urana（urana 传 dtSpan + dtMillis）；本方法只做"long→bit 载体→填 region"的机械动作。
     */
    @Override
    public void copyToInputFromLong(Span region, long value, long stream) {
        boolean[] bits = longToBoolArray(value);
        BoolVector inputVec = io.getInput().getVector();
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

    /**
     * 朴素前向（不存 fz）。子类可 override 在此前注入额外行为（如 standard_bnn 的门控重连）。
     */
    @Override
    public void forward(long stream) {
        BnnNetworkProcessor.forwardNoFz(networkData, io, stream);
    }

    @Override
    public void forwardForTraining(VectorBase fz, long stream) {
        BnnNetworkProcessor.forwardStoreFz(networkData, io, (BoolVector) fz, stream);
    }

    @Override
    public void backward(VectorBase fz, long stream) {
        try {
            BnnNetworkProcessor.backward(networkData, gradients, (BoolVector) fz, stream);
        } catch (Exception e) {
            throw new RuntimeException("BNN backward failed", e);
        }
    }

    @Override
    public void backwardAndUpdate(VectorBase fz, VectorBase aPrev, long stream) {
        try {
            BnnNetworkProcessor.backwardWithGradientDescent(
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
        // BNN 梯度计算：BnnGradientProcessor 签名要求 BnnOutputVector 包装。
        BnnOutputVector cur = new BnnOutputVector((BoolVector) currentOutput);
        BnnGradientProcessor.calculateOutputLayerGradient(
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
     * BNN 特定：把内部输入层梯度的 region 直接区间拷贝到内部输出层梯度的同一 region。
     */
    @Override
    public void injectOutputGradientFromInputGradient(Span region, long stream) {
        IntVector outputGradVec = gradients.getOutputLayerGradient().getVector();
        IntVector inputGradVec = gradients.getInputLayerGradient().getVector();
        outputGradVec.copyRegionFrom(inputGradVec, region, region, stream);
    }

    /**
     * BNN 特定：negateAndBinarize 的区间版——内部输入层梯度的 C region → C' 输入向量（全量）。
     */
    @Override
    public void gradientToInputFromInternal(Span region, VectorBase inputC, long stream) {
        IntVector inputGradVec = gradients.getInputLayerGradient().getVector();
        BoolVector dst = (BoolVector) inputC;
        BnnOps.negateAndBinarizeRegion(dst, fullSpan(dst), inputGradVec, region, stream);
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
