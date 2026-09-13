package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.IntVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.NnEncodingProfile;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.containers.BnnNetworkData;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.containers.io.BnnIO;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.containers.io.BnnIOLayerGradients;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.containers.io.value.BnnOutputVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.containers.io.value.BnnTargetVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.mapping.BnnOps;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.mapping.BnnNetworkProcessor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.mapping.training.BnnGradientProcessor;

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

    protected final BnnNetworkData networkData_original;
    protected final BnnIO io_original;
    protected final BnnIOLayerGradients gradients_original;
    protected final BnnTargetVector target_original;
    protected final int inputSize_original;
    protected final int outputSize_original;

    // === BNN 载体编码长度（家族共享，两个叶子 original_bnn/standard_bnn 载体相同）===
    // 设计原则（真善美第2/3条）：这些长度是 BNN 位平面/位编码的产物，不是 urana 意识域的模式。
    // 从 urana 的 InputVectorDomain/OutputVectorDomain 搬家至此（值不变，仅归属变更），
    // 使换 nn 实现（BNN→CNN）时 urana 零改动——新实现持自己的常量即可。
    // 不触碰"不能减小 FEELING_SPAN_LENGTH"约束（搬家不增减）。
    protected static final int BNN_FEELING_LENGTH_original = 1920 * 1080 * 24;   // F: 位平面（1920×1080 像素 × 24 bit/像素）
    protected static final int BNN_BEHAVIOR_LENGTH_original = 256;               // B: 行为位
    protected static final int BNN_DT_LENGTH_original = 64;                      // dt: long 拆 2×uint32 word
    protected static final int BNN_TIME_ORIENTATION_UNIT_original = 1;           // G 单分量: one-hot 每方位 1 bit

    /**
     * BNN 家族共享编码剖面（两个叶子工厂的 encodingProfile() 与本类实例方法均返回此实例）。
     * <p>
     * <b>public 而非 protected</b>：叶子工厂（{@code BnnNnFactory}/{@code StandardBnnNnFactory}）
     * {@code implements NnFactory}（不继承本类），无法访问 protected 字段。profile 是 BNN 家族对 urana
     * 的公开契约（已通过 {@link #encodingProfile()} 实例方法暴露），字段公开与该方法一致。
     * 而 {@link #BNN_FEELING_LENGTH_original} 等基础常量保持 protected——是 BNN 内部编码细节，叶子工厂不直接用。
     */
    public static final NnEncodingProfile BNN_PROFILE_original = new NnEncodingProfile(
            BNN_FEELING_LENGTH_original, BNN_BEHAVIOR_LENGTH_original, BNN_DT_LENGTH_original, BNN_TIME_ORIENTATION_UNIT_original);

    /**
     * 新建 bnn 网络（随机初始化权重）。
     *
     * @param inputSize  输入向量尺寸（由 urana 传入）。
     * @param outputSize 输出向量尺寸。
     */
    public AbstractBnnNeuralNetwork(int inputSize, int outputSize) {
        this.inputSize_original = inputSize;
        this.outputSize_original = outputSize;
        try {
            this.networkData_original = new BnnNetworkData(inputSize, outputSize, true);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to initialize BnnNetworkData", e);
        }
        this.io_original = new BnnIO(inputSize, outputSize);
        this.gradients_original = new BnnIOLayerGradients(inputSize, outputSize);
        this.target_original = new BnnTargetVector(outputSize);
    }

    /**
     * 从已加载权重构造（供叶子 {@code loadFromFile} 复用）。
     */
    protected AbstractBnnNeuralNetwork(BnnNetworkData networkData, int inputSize, int outputSize) {
        this.networkData_original = networkData;
        this.inputSize_original = inputSize;
        this.outputSize_original = outputSize;
        this.io_original = new BnnIO(inputSize, outputSize);
        this.gradients_original = new BnnIOLayerGradients(inputSize, outputSize);
        this.target_original = new BnnTargetVector(outputSize);
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
        BoolVector inputVec = io_original.getInput().getVector();
        inputVec.setRegion(region, (BoolVector) src, stream);
    }

    @Override
    public void copyFromInput(Span region, VectorBase dst, long stream) {
        BoolVector inputVec = io_original.getInput().getVector();
        dst.copyRegionFrom(inputVec, region, fullSpan(dst), stream);
    }

    @Override
    public void copyFromOutput(Span region, VectorBase dst, long stream) {
        BoolVector outputVec = io_original.getOutput().getVector();
        dst.copyRegionFrom(outputVec, region, fullSpan(dst), stream);
    }

    @Override
    public void copyToInputFromHost(Span region, boolean[] src, long stream) {
        BoolVector inputVec = io_original.getInput().getVector();
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
        BoolVector inputVec = io_original.getInput().getVector();
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
        BnnNetworkProcessor.forwardNoFz(networkData_original, io_original, stream);
    }

    @Override
    public void forwardForTraining(VectorBase fz, long stream) {
        BnnNetworkProcessor.forwardStoreFz(networkData_original, io_original, (BoolVector) fz, stream);
    }

    @Override
    public void backward(VectorBase fz, long stream) {
        try {
            BnnNetworkProcessor.backward(networkData_original, gradients_original, (BoolVector) fz, stream);
        } catch (Exception e) {
            throw new RuntimeException("BNN backward failed", e);
        }
    }

    @Override
    public void backwardAndUpdate(VectorBase fz, VectorBase aPrev, long stream) {
        try {
            BnnNetworkProcessor.backwardWithGradientDescent(
                    networkData_original, gradients_original, (BoolVector) fz, (BoolVector) aPrev, stream);
        } catch (Exception e) {
            throw new RuntimeException("BNN backwardAndUpdate failed", e);
        }
    }

    // ==================== 目标与梯度 ====================

    @Override
    public void setTarget(Span region, VectorBase src, long stream) {
        target_original.getVector().setRegion(region, (BoolVector) src, stream);
    }

    @Override
    public void computeOutputGradient(VectorBase currentOutput, Span region, long stream) {
        // BNN 梯度计算：BnnGradientProcessor 签名要求 BnnOutputVector 包装。
        BnnOutputVector cur = new BnnOutputVector((BoolVector) currentOutput);
        BnnGradientProcessor.calculateOutputLayerGradient(
                cur, target_original, gradients_original.getOutputLayerGradient(), region, stream);
    }

    @Override
    public void injectOutputGradient(Span region, VectorBase gradC, long stream) {
        IntVector outputGradVec = gradients_original.getOutputLayerGradient().getVector();
        outputGradVec.setRegion(region, (IntVector) gradC, stream);
    }

    @Override
    public void copyFromInputGradient(Span region, VectorBase dst, long stream) {
        IntVector inputGradVec = gradients_original.getInputLayerGradient().getVector();
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
        IntVector outputGradVec = gradients_original.getOutputLayerGradient().getVector();
        IntVector inputGradVec = gradients_original.getInputLayerGradient().getVector();
        outputGradVec.copyRegionFrom(inputGradVec, region, region, stream);
    }

    /**
     * BNN 特定：negateAndBinarize 的区间版——内部输入层梯度的 C region → C' 输入向量（全量）。
     */
    @Override
    public void gradientToInputFromInternal(Span region, VectorBase inputC, long stream) {
        IntVector inputGradVec = gradients_original.getInputLayerGradient().getVector();
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
        networkData_original.save(folderPath);
    }

    /**
     * BNN 数据向量 load：BoolVector.loadFromFile（与 createVector 返回 BoolVector 对称）。
     */
    @Override
    public VectorBase loadVector(String path) {
        return BoolVector.loadFromFile(path);
    }

    /**
     * BNN 梯度向量 load：IntVector.loadFromFile（与 createGradientVector 返回 IntVector 对称）。
     */
    @Override
    public VectorBase loadGradientVector(String path) {
        return IntVector.loadFromFile(path);
    }

    /**
     * 返回 BNN 家族共享编码剖面（{@link #BNN_PROFILE_original}）。
     * <p>
     * 与 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.NnFactory#encodingProfile()}
     * 对称：factory 级供实例化前查询，本实例方法供持 nn 引用的组件（AbstractGradCell/AbstractInferenceCell/UranaSystem）查询。
     */
    @Override
    public NnEncodingProfile encodingProfile() {
        return BNN_PROFILE_original;
    }

    @Override
    public void close() throws Exception {
        if (target_original != null) target_original.close();
        if (gradients_original != null) gradients_original.close();
        if (io_original != null) io_original.close();
        if (networkData_original != null) networkData_original.close();
    }

    private static Span fullSpan(VectorBase v) {
        return new Span(0, v.size()) {
        };
    }
}
