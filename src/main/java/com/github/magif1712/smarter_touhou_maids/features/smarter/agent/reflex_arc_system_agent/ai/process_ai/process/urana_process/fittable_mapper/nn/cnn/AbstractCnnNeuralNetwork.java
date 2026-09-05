package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.NnEncodingProfile;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn.containers.CnnFwTraceForBw;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn.containers.CnnHyperparameters;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn.containers.CnnNetworkData;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn.containers.io.CnnIO;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn.containers.io.CnnIOLayerGradients;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn.containers.io.value.CnnTargetVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn.mapping.inference.CnnInferenceOps;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn.mapping.training.CnnTrainingOps;

/**
 * CNN 家族的共享内核：把 CNN mechanics（{@link CnnNetworkData}/{@link CnnIO}/
 * {@link CnnIOLayerGradients}/{@link CnnTargetVector} 与 {@link CnnInferenceOps}/{@link CnnTrainingOps}）
 * 适配到 {@link INeuralNetwork} 机械级接口。
 * <p>
 * 本类是<b>家族核心</b>：住在 {@code nn.cnn} 包（家族层），叶子（{@code original_cnn}）是它的子类。
 * urana 只通过 {@link INeuralNetwork} 访问，不感知 CNN 内部模式（真善美第2条）。
 * <p>
 * 方法体照搬伪代码 {@code abstract_cnn_neural_network.py}，方向标记用 /* -&gt; *&#47; / /* &lt;- *&#47; 注释（设计原则第5条）。{@code close()} 伪代码为 {@code ...}，
 * 但释放逻辑已知，Java 侧真实释放（target→gradients→io→networkData 反序）。
 * 其余 {@code ...} 占位方法（{@code copyToInputFromHost}/{@code copyToInputFromLong}/
 * {@code computeOutputGradient}/{@code gradientToInput}/{@code gradientToInputFromInternal}）空体 + TODO。
 * <p>
 * <b>target 归属</b>：本类内部持 target（{@link #setTarget} 填它，{@link #backward} 直接用），urana 不持有 target——
 * target 是 nn 训练资源，不是意识体状态。
 */
public abstract class AbstractCnnNeuralNetwork implements INeuralNetwork {

    // === CNN 载体编码长度（家族共享）===
    // 设计原则（真善美第2/3条）：这些长度是 CNN RGB 浮点载体编码的产物，不是 urana 意识域的模式。
    // 从 urana 的 InputVectorDomain/OutputVectorDomain 搬家至此（值不变，仅归属变更），
    // 使换 nn 实现（CNN→未来新族）时 urana 零改动——新实现持自己的常量即可。
    public static final int CNN_FEELING_LENGTH = 1920 * 1080 * 3;   // F: RGB float（1920×1080 像素 × 3 通道）
    public static final int CNN_BEHAVIOR_LENGTH = 256;              // B: 行为分量
    public static final int CNN_DT_LENGTH = 1;                      // dt: 单 float
    public static final int CNN_TIME_ORIENTATION_UNIT = 4;          // G 单分量: one-hot 4 方位

    /**
     * CNN 家族共享编码剖面（叶子工厂的 {@code encodingProfile()} 与本类实例方法均返回此实例）。
     * public 而非 protected：叶子工厂 {@code implements NnFactory}（不继承本类），无法访问 protected 字段。
     */
    public static final NnEncodingProfile CNN_PROFILE = new NnEncodingProfile(
            CNN_FEELING_LENGTH, CNN_BEHAVIOR_LENGTH, CNN_DT_LENGTH, CNN_TIME_ORIENTATION_UNIT);

    /**
     * CNN 训练学习率（浮点权重梯度下降步长）。
     * BNN 的 bit 权重用 sign 函数固定步长更新，无此参数；CNN 浮点权重需 lr 缩放梯度。
     */
    public static final float CNN_LEARNING_RATE = 0.01f;

    protected final int inputSize;
    protected final int outputSize;
    protected final CnnNetworkData networkData;
    protected final CnnIO io;
    protected final CnnIOLayerGradients gradients;
    protected final CnnTargetVector target;

    /**
     * 新建 CNN 网络。{@code networkData} 为 {@code null} 时用默认结构（sizeA0=inputSize, sizeA1=outputSize）。
     * 构造期一次性 {@code cnnRefreshCache} 刷新派生缓存 idx/w（新建随机 p 与 loadFromFile 路径都需要）。
     */
    public AbstractCnnNeuralNetwork(int inputSize, int outputSize, CnnNetworkData networkData) {
        this.inputSize = inputSize;
        this.outputSize = outputSize;
        if (networkData != null) {
            this.networkData = networkData;
        } else {
            this.networkData = new CnnNetworkData(inputSize, outputSize);
        }
        this.io = new CnnIO(inputSize, outputSize);
        this.gradients = new CnnIOLayerGradients(inputSize, outputSize);
        this.target = new CnnTargetVector(outputSize);
        // 构造期一次性刷新 idx/w（非热路径，stream 0 + 同步）。
        // 新建（PCG 随机 p）与 loadFromFile 路径都需要：idx/w 是 p 的派生缓存，未刷新则前向读垃圾值。
        CnnInferenceOps.cnnRefreshCache(this.networkData.getHyperparameters(), 0L /* -> */);
    }

    private static Span fullSpan(VectorBase v) {
        return new Span(0, v.size()) {};
    }

    @Override
    public NnEncodingProfile encodingProfile() {
        return CNN_PROFILE;
    }

    // ==================== 感觉载体契约（CNN 家族：FloatVector RGB float）====================

    /**
     * CNN 家族的感觉载体是 FloatVector（浮点激活网络）。
     * 载体类型知识留在本家族，上层经 ai 链拿产品（缓冲实例），无类型开关。
     */
    @Override
    public VectorBase newFeelingBuffer(int feelingLength) {
        return new FloatVector(feelingLength);
    }

    // ==================== 行为载体契约（CNN 家族：FloatVector + float→bit 转换）====================

    /**
     * CNN 家族的行为载体是 FloatVector（浮点激活网络）。
     * 使用 mapped pinned memory（零拷贝），主线程读取零 CUDA 调用，不 flush WDDM 命令缓冲。
     */
    @Override
    public VectorBase newBehaviorBuffer(int behaviorLength) {
        return FloatVector.mapped(behaviorLength);
    }

    /**
     * CNN 行为读取：FloatVector → float[] → 阈值化 → bit-packed int[]。
     * <p>
     * float→bit 转换是 CNN 家族的内部模式（σ(z)≥0.5 → bit 1，即 sigmoid 判决边界），
     * 与 BNN 的 sign 函数判决对齐——effector 收到统一 bit-packed int[]，接口零改动。
     * 零 CUDA 调用：纯 host memcpy 读 mapped pinned memory，不 flush WDDM 命令缓冲。
     */
    @Override
    public void readBehaviorTo(VectorBase behaviorBuffer, int[] dst, long stream) {
        if (!(behaviorBuffer instanceof FloatVector fv)) {
            throw new IllegalArgumentException("CNN readBehaviorTo requires FloatVector");
        }
        int floatCount = fv.size();
        float[] floats = new float[floatCount];
        // 零 CUDA 调用：纯 host memcpy 读 mapped pinned memory
        fv.readMappedToJava(floats, floatCount);
        // float→bit 阈值化 + LSB-first 打包
        int wordCount = (floatCount + 31) / 32;
        for (int i = 0; i < wordCount; i++) {
            int word = 0;
            int base = i * 32;
            for (int b = 0; b < 32; b++) {
                int idx = base + b;
                if (idx >= floatCount) break;
                if (floats[idx] >= 0.5f) {
                    word |= (1 << b);
                }
            }
            if (i < dst.length) {
                dst[i] = word;
            }
        }
    }

    // ==================== CnnIO 区域读写 ====================

    @Override
    public void copyToInput(/* <- */ Span region, VectorBase src, long stream) {
        FloatVector inputVec = io.getInput().getVector();
        inputVec.setRegion(/* <- */ region, (FloatVector) src, stream);
    }

    @Override
    public void copyFromInput(Span region, long stream /* -> */, VectorBase dst) {
        FloatVector inputVec = io.getInput().getVector();
        dst.copyRegionFrom(/* <- */ inputVec, region, fullSpan(dst), stream);
    }

    @Override
    public void copyFromOutput(Span region, long stream /* -> */, VectorBase dst) {
        FloatVector outputVec = io.getOutput().getVector();
        dst.copyRegionFrom(/* <- */ outputVec, region, fullSpan(dst), stream);
    }

    @Override
    public void copyToInputFromHost(/* <- */ Span region, float[] src, long stream) {
        // TODO 待 C 侧/设计落地
    }

    @Override
    public void copyToInputFromLong(/* <- */ Span region, long value, long stream) {
        // TODO 待 C 侧/设计落地
    }

    @Override
    public void forward(VectorBase x, long stream /* -> */, VectorBase y, Object fwTraceForBw) {
        CnnInferenceOps.cnnForwardLayer(io.getA0(), networkData.getHyperparameters(), stream /* -> */, io.getA1(), (CnnFwTraceForBw) fwTraceForBw);
    }

    @Override
    public void backward(Object fwTraceForBw, VectorBase t, long stream /* -> */, VectorBase bufTc, Object bufHp) {
        CnnHyperparameters hp = networkData.getHyperparameters();
        CnnTrainingOps.cnnBackwardLayer((CnnFwTraceForBw) fwTraceForBw, hp, target.getVector(), stream /* -> */, io.getA0(), gradients.getDzWorkspace(), gradients.getInputLayerGradient().getVector(), (FloatVector) bufTc, (CnnHyperparameters) bufHp, CNN_LEARNING_RATE);
    }

    @Override
    public CnnHyperparameters getHyperparameters() {
        return networkData.getHyperparameters();
    }

    @Override
    public void setTarget(/* <- */ Span region, VectorBase src, long stream) {
        target.getVector().setRegion(/* <- */ region, (FloatVector) src, stream);
    }

    @Override
    public void computeOutputGradient(/* <- */ VectorBase currentOutput, Span region, long stream) {
        // TODO 待 C 侧/设计落地
    }

    @Override
    public void injectOutputGradient(/* <- */ Span region, VectorBase gradC, long stream) {
        FloatVector outputGradVec = gradients.getOutputLayerGradient().getVector();
        outputGradVec.setRegion(/* <- */ region, (FloatVector) gradC, stream);
    }

    @Override
    public void copyFromInputGradient(Span region, long stream /* -> */, VectorBase dst) {
        FloatVector inputGradVec = gradients.getInputLayerGradient().getVector();
        dst.copyRegionFrom(/* <- */ inputGradVec, region, fullSpan(dst), stream);
    }

    @Override
    public void gradientToInput(/* <- */ VectorBase gradC, VectorBase inputC, long stream) {
        // TODO 待 C 侧/设计落地
    }

    @Override
    public void injectOutputGradientFromInputGradient(/* <- */ Span region, long stream) {
        FloatVector outputGradVec = gradients.getOutputLayerGradient().getVector();
        FloatVector inputGradVec = gradients.getInputLayerGradient().getVector();
        outputGradVec.copyRegionFrom(/* <- */ inputGradVec, region, region, stream);
    }

    @Override
    public void gradientToInputFromInternal(/* <- */ Span region, VectorBase inputC, long stream) {
        // TODO 待 C 侧/设计落地
    }

    @Override
    public void zeroGradient(long stream /* -> */, VectorBase gradVec) {
        ((FloatVector) gradVec).multiplyByScalar(0f, stream);
    }

    @Override
    public void zeroVector(long stream /* -> */, VectorBase vec) {
        ((FloatVector) vec).multiplyByScalar(0f, stream);
    }

    @Override
    public FloatVector createVector(int size) {
        return new FloatVector(size);
    }

    @Override
    public FloatVector createGradientVector(int size) {
        return new FloatVector(size);
    }

    @Override
    public CnnFwTraceForBw createFwTraceForBw() {
        // z/y 同 sizeA1=outputSize：前向写 z（pre-activation）与 y（σ(z)），反向读 y 算 δ。
        return new CnnFwTraceForBw(new FloatVector(outputSize), new FloatVector(outputSize));
    }

    @Override
    public void save(String folderPath) {
        networkData.save(folderPath);
    }

    @Override
    public FloatVector loadVector(String path) {
        return FloatVector.loadFromFile(path);
    }

    @Override
    public FloatVector loadGradientVector(String path) {
        return FloatVector.loadFromFile(path);
    }

    @Override
    public void close() throws Exception {
        target.close();
        gradients.close();
        io.close();
        networkData.close();
    }
}