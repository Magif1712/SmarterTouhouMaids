package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.bnn;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.IntVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.NnEncodingProfile;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.BnnHyperparameters;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.BnnNetworkData;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.io.BnnIO;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.io.BnnIOLayerGradients;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.io.value.BnnOutputVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.io.value.BnnTargetVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.mapping.BnnNetworkProcessor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.mapping.BnnOps;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.mapping.training.BnnGradientProcessor;

/**
 * BNN 家族的共享内核（新版）：把原初代理的 BNN mechanics（{@link BnnNetworkData}/{@link BnnIO}/
 * {@link BnnIOLayerGradients}/{@link BnnTargetVector} 与 {@link BnnNetworkProcessor}/{@link BnnOps}）
 * 适配到新版 {@link INeuralNetwork} 机械级接口。
 * <p>
 * 与原初代理的 {@code AbstractBnnNeuralNetwork} 的关系：原初代理那个实现的是<b>原初代理的</b>
 * {@code INeuralNetwork}（forward/backward 签名、DPS 方向、载体契约均不同），无法直接复用。
 * 本类<b>新写</b>，实现<b>新版</b> {@link INeuralNetwork}，内部委托原初代理的容器/算子
 * （这些是纯数据结构 + 静态 native 包装，不依赖任何 INeuralNetwork 接口，可直接复用）。
 * <p>
 * <b>载体</b>：BNN 用 BoolVector（位压缩）/IntVector（int 梯度），与 CNN 的 FloatVector 对称。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：BNN 的具体模式（b/p/q/l/r 权重、BoolVector/IntVector 载体、forwardStoreFz、
 *       两阶段 BPTT 中间态等）藏在家族内核里；urana 只通过 {@link INeuralNetwork} 访问，不感知。</li>
 *   <li><b>第3条</b>：把"可换 nn"实在化为本接口 + 一族实现；本类与新 {@code AbstractCnnNeuralNetwork}
 *       并列，任一可被删换而不改 urana（"上层 ai 系统中的神经网络 nn 可以切换到 bnn 也可以切换到 cnn"）。</li>
 *   <li><b>第4条</b>：把"BNN 适配新版 INeuralNetwork"这个不实在约束，实在化为本抽象类的方法签名。</li>
 * </ul>
 * <p>
 * 方法签名照搬新版 {@link INeuralNetwork}，方向标记用 {@code /* -&gt; *&#47;} / {@code /* &lt;- *&#47;} 注释
 * （设计原则第5条）。nn 自身被改写的方法（copyToInput/setTarget/injectOutputGradient 等）用
 * {@code /* &lt;- *&#47;}（左边出参=self，右边入参）；nn 自身不被改写、注入缓冲区为出参的方法
 * （copyFromInput/forward/zeroGradient 等）用 {@code /* -&gt; *&#47;}（左入右出）。
 * <p>
 * <b>target 归属</b>：本类内部持 target（{@link #setTarget} 填它，{@link #computeOutputGradient}
 * 直接用），urana 不持有 target——target 是 nn 训练资源，不是意识体状态。
 */
public abstract class AbstractBnnNeuralNetwork implements INeuralNetwork {

    // === BNN 载体编码长度（家族共享，与原初代理同款）===
    // 设计原则（真善美第2/3条）：这些长度是 BNN 位平面/位编码的产物，不是 urana 意识域的模式。
    // 值照搬原初代理 AbstractBnnNeuralNetwork，使新旧两代理 BNN 行为一致（对照实验前提）。
    // 不触碰"不能减小 FEELING_SPAN_LENGTH"约束。
    public static final int BNN_FEELING_LENGTH = 1920 * 1080 * 24;   // F: 位平面（1920×1080 像素 × 24 bit/像素）
    public static final int BNN_BEHAVIOR_LENGTH = 256;               // B: 行为位
    public static final int BNN_DT_LENGTH = 64;                      // dt: long 拆 2×uint32 word
    public static final int BNN_TIME_ORIENTATION_UNIT = 1;           // G 单分量: one-hot 每方位 1 bit

    /**
     * BNN 家族共享编码剖面（叶子工厂的 {@code encodingProfile()} 与本类实例方法均返回此实例）。
     * public 而非 protected：叶子工厂 {@code implements NnFactory}（不继承本类），无法访问 protected 字段。
     */
    public static final NnEncodingProfile BNN_PROFILE = new NnEncodingProfile(
            BNN_FEELING_LENGTH, BNN_BEHAVIOR_LENGTH, BNN_DT_LENGTH, BNN_TIME_ORIENTATION_UNIT);

    protected final int inputSize;
    protected final int outputSize;
    protected final BnnNetworkData networkData;
    protected final BnnIO io;
    protected final BnnIOLayerGradients gradients;
    protected final BnnTargetVector target;

    /**
     * 新建 BNN 网络（随机初始化权重）。
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
     * 从磁盘加载 BNN 权重，自动反推尺寸（叶子 {@code loadFromFile} 共用）。
     */
    protected static BnnNetworkData loadNetworkData(String folderPath) {
        return BnnNetworkData.loadFromFile(folderPath);
    }

    private static Span fullSpan(VectorBase v) {
        return new Span(0, v.size()) {};
    }

    @Override
    public NnEncodingProfile encodingProfile() {
        return BNN_PROFILE;
    }

    // ==================== 感觉载体契约（BNN 家族：BoolVector 位平面）====================

    /**
     * BNN 家族的感觉载体是 BoolVector（位压缩）。
     * 载体类型知识留在本家族，上层经 ai 链拿产品（缓冲实例），无类型开关。
     */
    @Override
    public VectorBase newFeelingBuffer(int feelingLength) {
        return new BoolVector(feelingLength);
    }

    // ==================== 行为载体契约（BNN 家族：BoolVector 直接 bit-packed）====================

    @Override
    public VectorBase newBehaviorBuffer(int behaviorLength) {
        return new BoolVector(behaviorLength);
    }

    /**
     * BNN 行为读取：BoolVector 直接读 bit-packed int[]（行为已是位压缩，无 float→bit 转换）。
     * 与 CNN 的 float→bit 阈值化不同——BNN 行为天然是 bit，effector 收到统一 int[]，接口零改动。
     */
    @Override
    public void readBehaviorTo(VectorBase behaviorBuffer, int[] dst, long stream) {
        if (!(behaviorBuffer instanceof BoolVector bv)) {
            throw new IllegalArgumentException("BNN readBehaviorTo requires BoolVector");
        }
        int wordCount = (bv.size() + 31) / 32;
        if (wordCount > dst.length) {
            wordCount = dst.length;
        }
        bv.copyToHost(dst, wordCount);
    }

    // ==================== BnnIO 区域读写 ====================

    @Override
    public void copyToInput(/* <- */ Span region, VectorBase src, long stream) {
        BoolVector inputVec = io.getInput().getVector();
        inputVec.setRegion(/* <- */ region, (BoolVector) src, stream);
    }

    @Override
    public void copyFromInput(Span region, long stream /* -> */, VectorBase dst) {
        BoolVector inputVec = io.getInput().getVector();
        dst.copyRegionFrom(/* <- */ inputVec, region, fullSpan(dst), stream);
    }

    @Override
    public void copyFromOutput(Span region, long stream /* -> */, VectorBase dst) {
        BoolVector outputVec = io.getOutput().getVector();
        dst.copyRegionFrom(/* <- */ outputVec, region, fullSpan(dst), stream);
    }

    /**
     * BNN host 载体是 boolean[]，新版接口签名硬编码 float[]（CNN 载体）。
     * BnnMapper.assembleX 直接操作 bufX（BoolVector.copyRegionFromHost），不经此法，故抛异常 fail-fast。
     * 与 CNN 的 TODO 空体对称——此法在新版架构中未被调用。
     */
    @Override
    public void copyToInputFromHost(/* <- */ Span region, float[] src, long stream) {
        throw new UnsupportedOperationException("BNN host carrier is boolean[], not float[]; use BnnMapper.assembleX via BoolVector.copyRegionFromHost instead");
    }

    /**
     * BNN 特定编码：long 拆成 64 个 boolean，走 BoolVector.copyRegionFromHost 的 bit 级路径。
     * <p>
     * dt 语义在 urana（urana 传 dtSpan + dtMillis）；本方法只做"long→bit 载体→填 region"的机械动作。
     */
    @Override
    public void copyToInputFromLong(/* <- */ Span region, long value, long stream) {
        boolean[] bits = longToBoolArray(value);
        BoolVector inputVec = io.getInput().getVector();
        inputVec.copyRegionFromHost(/* <- */ region, bits, stream);
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
     * 适配新版 forward 签名：fwTraceForBw 非 null 时走 forwardStoreFz（训练前向，存 fz），
     * 为 null 时走 forwardNoFz（纯推理）。x/y 参数忽略——IO 由 mapper 外层 copyToInput/copyFromOutput 处理
     * （与 CNN 实现一致：CNN forward 也不用 x/y，直接操作 io）。
     */
    @Override
    public void forward(VectorBase x, long stream /* -> */, VectorBase y, Object fwTraceForBw) {
        if (fwTraceForBw != null) {
            BnnNetworkProcessor.forwardStoreFz(networkData, io, (BoolVector) fwTraceForBw, stream);
        } else {
            BnnNetworkProcessor.forwardNoFz(networkData, io, stream);
        }
    }

    /**
     * 适配新版 backward 签名：fwTraceForBw 即 fz（前向中间态），t 已由 mapper 外层 setTarget 填入，
     * bufHp 忽略（BNN 用 sign 固定步长，无学习率缩放）。
     * <p>
     * 阶段一（bufTc != null）：调用 BnnNetworkProcessor.backward 更新内部 gradients，
     * 再把 inputLayerGradient 经 negateAndBinarize 转成前向 C（BoolVector）外拷到 bufTc。
     * 阶段二（bufTc == null）：只更新权重，跳过梯度外拷。
     * <p>
     * <b>bufTc 载体决策</b>：伪代码 grad_cell_op.py 中 tC 兼任 fw 的 C 输入（前向）与 bw 的 bufTc 出参
     * （梯度外拷贝）。CNN 下两者同体（FloatVector）无矛盾；BNN 下前向=BoolVector、梯度=IntVector 不兼容。
     * 故 tC 统一用前向载体（createVector，BoolVector），backward 外拷时用 negateAndBinarize 把
     * IntVector 梯度（±1）转成 BoolVector 前向 C（0/1）——与原初代理 gradientToInput 同款 bit 编码，
     * 藏于 BNN 家族内核，urana 经接口只拿前向形态（真善美第2/3条）。
     */
    @Override
    public void backward(Object fwTraceForBw, VectorBase t, long stream /* -> */, VectorBase bufTc, Object bufHp) {
        try {
            BnnNetworkProcessor.backward(networkData, gradients, (BoolVector) fwTraceForBw, stream);
        } catch (Exception e) {
            throw new RuntimeException("BNN backward failed", e);
        }
        // 阶段一：把输入层梯度（IntVector ±1）negateAndBinarize 为前向 C（BoolVector 0/1）外拷到 bufTc
        if (bufTc != null) {
            IntVector inputGrad = gradients.getInputLayerGradient().getVector();
            BnnOps.negateAndBinarize(inputGrad, (BoolVector) bufTc, stream);
        }
    }

    @Override
    public BnnHyperparameters getHyperparameters() {
        return networkData.getHyperparameters();
    }

    @Override
    public void setTarget(/* <- */ Span region, VectorBase src, long stream) {
        target.getVector().setRegion(/* <- */ region, (BoolVector) src, stream);
    }

    /**
     * 计算输出层梯度（基于内部 target 与给定 currentOutput 的差）。
     * 照搬原初代理实现：currentOutput 包装为 BnnOutputVector（不拥有，只读），调 BnnGradientProcessor。
     */
    @Override
    public void computeOutputGradient(/* <- */ VectorBase currentOutput, Span region, long stream) {
        BnnOutputVector cur = new BnnOutputVector((BoolVector) currentOutput);
        BnnGradientProcessor.calculateOutputLayerGradient(
                cur, target, gradients.getOutputLayerGradient(), region, stream);
    }

    @Override
    public void injectOutputGradient(/* <- */ Span region, VectorBase gradC, long stream) {
        IntVector outputGradVec = gradients.getOutputLayerGradient().getVector();
        outputGradVec.setRegion(/* <- */ region, (IntVector) gradC, stream);
    }

    @Override
    public void copyFromInputGradient(Span region, long stream /* -> */, VectorBase dst) {
        IntVector inputGradVec = gradients.getInputLayerGradient().getVector();
        dst.copyRegionFrom(/* <- */ inputGradVec, region, fullSpan(dst), stream);
    }

    /**
     * BNN 特定：negateAndBinarize（IntVector 梯度 → BoolVector 输入）。
     */
    @Override
    public void gradientToInput(/* <- */ VectorBase gradC, VectorBase inputC, long stream) {
        BnnOps.negateAndBinarize((IntVector) gradC, (BoolVector) inputC, stream);
    }

    /**
     * BNN 特定：把内部输入层梯度的 region 直接区间拷贝到内部输出层梯度的同一 region。
     */
    @Override
    public void injectOutputGradientFromInputGradient(/* <- */ Span region, long stream) {
        IntVector outputGradVec = gradients.getOutputLayerGradient().getVector();
        IntVector inputGradVec = gradients.getInputLayerGradient().getVector();
        outputGradVec.copyRegionFrom(/* <- */ inputGradVec, region, region, stream);
    }

    /**
     * BNN 特定：negateAndBinarize 的区间版——内部输入层梯度的 C region → C' 输入向量（全量）。
     */
    @Override
    public void gradientToInputFromInternal(/* <- */ Span region, VectorBase inputC, long stream) {
        IntVector inputGradVec = gradients.getInputLayerGradient().getVector();
        BoolVector dst = (BoolVector) inputC;
        BnnOps.negateAndBinarizeRegion(dst, fullSpan(dst), inputGradVec, region, stream);
    }

    /**
     * BNN 特定：IntVector.multiplyByScalar(0)。
     */
    @Override
    public void zeroGradient(long stream /* -> */, VectorBase gradVec) {
        ((IntVector) gradVec).multiplyByScalar(0, stream);
    }

    /**
     * BNN 特定：BoolVector 用 copyRegionFromHost(false[])。
     */
    @Override
    public void zeroVector(long stream /* -> */, VectorBase vec) {
        BoolVector bv = (BoolVector) vec;
        bv.copyRegionFromHost(/* <- */ fullSpan(bv), new boolean[bv.size()], stream);
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

    /**
     * BNN 的 fwTrace 是 fz（前向中间态 BoolVector，outputSize）。
     * 与 CNN 的 CnnFwTraceForBw（含 z/y 两个 FloatVector）不同——家族私有类型，返回 Object。
     */
    @Override
    public Object createFwTraceForBw() {
        return new BoolVector(outputSize);
    }

    // ==================== 序列化 ====================

    @Override
    public void save(String folderPath) {
        networkData.save(folderPath);
    }

    @Override
    public VectorBase loadVector(String path) {
        return BoolVector.loadFromFile(path);
    }

    @Override
    public VectorBase loadGradientVector(String path) {
        return IntVector.loadFromFile(path);
    }

    @Override
    public void close() throws Exception {
        if (target != null) target.close();
        if (gradients != null) gradients.close();
        if (io != null) io.close();
        if (networkData != null) networkData.close();
    }
}