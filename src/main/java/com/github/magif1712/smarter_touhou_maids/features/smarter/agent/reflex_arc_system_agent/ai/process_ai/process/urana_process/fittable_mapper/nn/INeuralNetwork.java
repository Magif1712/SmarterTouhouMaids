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
}
