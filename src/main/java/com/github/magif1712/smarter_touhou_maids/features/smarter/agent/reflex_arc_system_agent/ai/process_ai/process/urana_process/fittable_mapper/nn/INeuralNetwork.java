package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;

/**
 * 神经网络机械级接口：把"urana 可换 nn"这个不实在约束，实在化为一组方法签名（真善美第4条）。
 * <p>
 * urana（意识域）只通过本接口使用 nn，不感知具体 nn 家族（BNN/CNN/未来新族）的内部模式——
 * 权重类型、激活载体、前向/反向的具体 kernel 都藏在各 nn 家族内核里（真善美第2条）。
 * <p>
 * 继承 {@link AutoCloseable}：nn 持有显存资源，{@link #close()} 由实现真实释放（对应伪代码 close 的释放语义）。
 * <p>
 * 设计原则（真善美第3条）：把"可换 nn"实在化为本接口 + 一族实现；任一 nn 家族可被删换而不改 urana。
 * <p>
 * 方法签名照搬伪代码 {@code i_neural_network.py}，方向标记用 /* -&gt; *&#47; / /* &lt;- *&#47; 注释（设计原则第5条）：
 * nn 自身被改写的方法（copyToInput/setTarget/injectOutputGradient 等）用 /* &lt;- *&#47;（左边出参=self，右边入参）；
 * nn 自身不被改写、注入缓冲区为出参的方法（copyFromInput/forward/zeroGradient 等）用 /* -&gt; *&#47;（左入右出）。
 * <p>
 * 类型决策：{@code fwTraceForBw}/{@code bufHp}/{@code getHyperparameters()} 返回 {@link Object}——
 * 伪代码 duck-typed，各 nn 家族的 trace/hp 是家族私有类型无公共基类，不为此造新接口避免过度设计（真善美第2条：
 * 不实在的约束不实在化）。{@code createFwTraceForBw()} 未在伪代码接口声明，但 OriginalMapper 调
 * {@code self.nn.createFwTraceForBw()}，Java 静态类型需要，故补入本接口。
 */
public interface INeuralNetwork extends AutoCloseable {

    void copyToInput(/* <- */ Span region, VectorBase src, long stream);

    void copyFromInput(Span region, long stream /* -> */, VectorBase dst);

    void copyFromOutput(Span region, long stream /* -> */, VectorBase dst);

    /**
     * 从 host 数组填输入区间。CNN host 载体为 float[]。
     * <p>
     * TODO 待 C 侧/设计落地。
     */
    void copyToInputFromHost(/* <- */ Span region, float[] src, long stream);

    /**
     * 用 long 标量填输入区间。
     * <p>
     * TODO 待 C 侧/设计落地。
     */
    void copyToInputFromLong(/* <- */ Span region, long value, long stream);

    void forward(VectorBase x, long stream /* -> */, VectorBase y, Object fwTraceForBw);

    void backward(Object fwTraceForBw, VectorBase t, long stream /* -> */, VectorBase bufTc, Object bufHp);

    Object getHyperparameters();

    void setTarget(/* <- */ Span region, VectorBase src, long stream);

    /**
     * TODO 待 C 侧/设计落地。
     */
    void computeOutputGradient(/* <- */ VectorBase currentOutput, Span region, long stream);

    void injectOutputGradient(/* <- */ Span region, VectorBase gradC, long stream);

    void copyFromInputGradient(Span region, long stream /* -> */, VectorBase dst);

    /**
     * TODO 待 C 侧/设计落地。
     */
    void gradientToInput(/* <- */ VectorBase gradC, VectorBase inputC, long stream);

    void injectOutputGradientFromInputGradient(/* <- */ Span region, long stream);

    /**
     * TODO 待 C 侧/设计落地。
     */
    void gradientToInputFromInternal(/* <- */ Span region, VectorBase inputC, long stream);

    void zeroGradient(long stream /* -> */, VectorBase gradVec);

    void zeroVector(long stream /* -> */, VectorBase vec);

    VectorBase createVector(int size);

    VectorBase createGradientVector(int size);

    /**
     * 创建前向 trace（供反向使用）。伪代码接口未声明但 urana 映射器调用，Java 静态类型需要补入。
     */
    Object createFwTraceForBw();

    void save(String folderPath);

    VectorBase loadVector(String path);

    VectorBase loadGradientVector(String path);

    NnEncodingProfile encodingProfile();

    /**
     * 感觉载体契约（可选能力）：创建本 nn 家族的感觉缓冲区。
     * <p>
     * 载体类型由 nn 家族定义（BNN→BoolVector，CNN→FloatVector），长度由上层 domain 传入——
     * 载体类型知识不离开 nn 家族，上层（agent 经 ai/process/mapper 链）只拿到产品（缓冲实例），
     * 无类型开关、无平行布尔量（真善美第2条：载体是 nn 家族的模式，不是 ai 链各层的模式）。
     * 经 mapper→process→ai 逐层 default 委托上浮（镜像 {@link #encodingProfile()} 的 profile 路径）。
     * <p>
     * 未发布的家族走默认（抛异常）——上层组装非法组合时即 fail-fast。
     *
     * @param feelingLength 感觉区长度（载体单位：BoolVector=bit，FloatVector=元素；来自上层 domain 的 feeling span）。
     */
    default VectorBase newFeelingBuffer(int feelingLength) {
        throw new UnsupportedOperationException("此 nn 家族未发布感觉载体契约（newFeelingBuffer）");
    }

    /**
     * 行为载体契约（可选能力）：创建本 nn 家族的行为缓冲区。
     * <p>
     * 与 {@link #newFeelingBuffer} 对称：行为输出侧的载体也由 nn 家族定义
     * （BNN→BoolVector 位平面，CNN→FloatVector 浮点），经 mapper→process→ai 逐层 default 委托上浮。
     * agent 据此创建 behaviorChannel 的 buffer（无类型开关）。
     *
     * @param behaviorLength 行为区长度（载体单位：BoolVector=bit，FloatVector=元素）。
     */
    default VectorBase newBehaviorBuffer(int behaviorLength) {
        throw new UnsupportedOperationException("此 nn 家族未发布行为载体契约（newBehaviorBuffer）");
    }

    /**
     * 行为读取契约（可选能力）：把行为缓冲区的载体数据读出到 int[]（effector 期望的 bit-packed 格式）。
     * <p>
     * 载体类型知识留在本家族：BNN 直接从 mapped host 内存读 int[]（零拷贝）；
     * CNN 读 float[] 后阈值化为 bit 再打包为 int[]（float→bit 转换是 CNN 家族的内部模式）。
     * 上层（agent）只拿到统一的 int[] 格式，effector 接口零改动（真善美第2条）。
     *
     * @param behaviorBuffer 行为缓冲区（由 {@link #newBehaviorBuffer} 创建）。
     * @param dst           接收 bit-packed 数据的 int[]（LSB-first，长度 >= ceil(behaviorLen/32)）。
     * @param stream        CUDA 流（同步用；mapped 模式可忽略，非 mapped 需 stream-aware D2H）。
     */
    default void readBehaviorTo(VectorBase behaviorBuffer, int[] dst, long stream) {
        throw new UnsupportedOperationException("此 nn 家族未发布行为读取契约（readBehaviorTo）");
    }
}