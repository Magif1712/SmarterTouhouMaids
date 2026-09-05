package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.InputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.OutputVectorDomain;

/**
 * 可拟合映射器接口：把"urana 经映射器使用 nn"这个不实在约束，实在化为一组方法签名（真善美第4条）。
 * <p>
 * 映射器负责把意识域的语义向量（C/F/G/dt 输入、C/F/B 输出）装配到 nn 的输入/输出缓冲区，
 * 并驱动 nn 前向/反向/清零梯度。urana 通过映射器使用 nn，对 nn 无感知（真善美第2条）。
 * <p>
 * 方法签名照搬伪代码 {@code i_fittable_mapper.py}，方向标记用 /* -&gt; *&#47; 注释（设计原则第5条）：
 * 左边入参，右边出参（必然注入的缓冲区）。
 * <p>
 * 类型决策：{@code assembleX} 的 {@code G} 用 {@code boolean[]}、{@code dt} 用 {@code long}
 * （照搬伪代码调用方 {@code G_seq[i]}/{@code dt} 的原始类型），类型转换在 mapper 实现内部完成，
 * 调用方无需额外转换缓冲（设计原则第2条：伪代码没有的模式 Java 也不应有）。
 * <p>
 * 类型决策：{@code bw} 的 {@code bufMapper} 用本接口自身（伪代码 duck-typed 读 {@code buf_mapper.nn}），
 * 具体实现（{@code OriginalMapper}）内 cast 到自身以访问其 {@code nn} 字段。
 * <p>
 * <b>接口而非具体类</b>（真善美第2条）：UranaSystem/UranaState/UranaFunction 只依赖本接口，
 * 附属模组可在 process→nn 之间插入实现本接口的装饰器层（如日志/量化/蒸馏），
 * 无需 mixin。本接口含 inputDomain/outputDomain 访问与 close 生命周期——urana 上层经接口取这些，
 * 不感知具体 mapper 家族（真善美第2条 + 第4条）。
 */
public interface FittableMapper extends AutoCloseable {

    void assembleX(VectorBase C, VectorBase F, boolean[] G, long dt, long stream /* -> */, VectorBase bufX);

    void assembleT(VectorBase C, VectorBase F, VectorBase B, long stream /* -> */, VectorBase bufT);

    void fw(VectorBase x, long stream /* -> */, VectorBase y, Object fwTraceForBw);

    void bw(Object fwTraceForBw, VectorBase t, long stream /* -> */, VectorBase bufTc, FittableMapper bufMapper);

    void zeroGradient(long stream /* -> */, VectorBase gradVec);

    /**
     * 清零向量（非梯度载体，如继承信息 inheritance）。
     * <p>
     * 与 {@link #zeroGradient} 对称——zeroGradient 清零梯度向量（IntVector/FloatVector），
     * zeroVector 清零普通向量（BoolVector/FloatVector）。载体类型由所持 nn 家族决定，
     * mapper 委托 {@code nn.zeroVector}，不感知具体载体（真善美第2/3条）。
     */
    void zeroVector(long stream /* -> */, VectorBase vec);

    // ---- Java adaptation（伪代码无此方法，对应 y.C/y.F 的 Java 实在化，见实现类注释）----

    /**
     * 对应伪代码 {@code y.C}：从输出向量 y 抽取 C（继承信息）区域。
     * 返回内部工作缓冲引用，调用方须立即消费（下次调用覆盖）。
     */
    VectorBase extractC(VectorBase y, long stream);

    /**
     * 对应伪代码 {@code y.F}：从输出向量 y 抽取 F（感觉）区域。
     * 返回内部工作缓冲引用，调用方须立即消费（下次调用覆盖）。
     */
    VectorBase extractF(VectorBase y, long stream);

    // ---- 资源工厂与生命周期委托（伪代码在 OriginalMapper 声明，Java 接口需要补入以供 AncSlider 等调用方使用）----

    VectorBase createVector(int size);

    VectorBase createGradientVector(int size);

    Object createFwTraceForBw();

    void save(String folderPath);

    VectorBase loadVector(String path);

    VectorBase loadGradientVector(String path);

    // ---- domain 访问 + 生命周期（urana 上层经接口取这些，不感知具体 mapper 家族）----

    /**
     * 照搬伪代码 {@code mapper.inputDomain} 的直接属性访问：返回输入向量布局（C@F@G@dt 各 span）。
     * 装饰器层应转发到被装饰的 mapper（同 subRegistryId 链上的最终 mapper）。
     */
    InputVectorDomain getInputDomain();

    /**
     * 照搬伪代码 {@code mapper.outputDomain} 的直接属性访问：返回输出向量布局（C@F@B 各 span）。
     * 装饰器层应转发到被装饰的 mapper。
     */
    OutputVectorDomain getOutputDomain();

    /**
     * 取本 mapper 所持 nn 的超参数（bufHp，反向时传入 nn.backward 的最后一个参数）。
     * <p>
     * 伪代码 duck-typed 读 {@code buf_mapper.nn.getHyperparameters()}——各 nn 家族的 hp 是
     * 家族私有类型无公共基类，故返回 {@link Object}（真善美第2条：不实在的约束不实在化）。
     * <p>
     * <b>设计动机</b>（真善美第3条）：bw 签名上接受任意 FittableMapper 作为 bufMapper，
     * 实现内须经本方法而非强转具体类访问 bufHp——否则附属模组插入的装饰器层 S' 会让
     * {@code Ext(S'.Y) ⊄ Ext(T.Y)}（强转 ClassCastException）。装饰器层应转发到被装饰的 mapper。
     */
    Object getHyperparameters();

    // ---- 感觉载体契约（nn 家族契约经 mapper 上浮；装饰器层应转发到被装饰的 mapper）----

    /**
     * 创建感觉缓冲区（可选能力，default 委托链的 mapper 段）。
     * <p>
     * 长度取自本 mapper 的 inputDomain feeling span，载体类型由所持 nn 家族决定
     * （{@code nn.newFeelingBuffer(length)}）——mapper 不感知载体，只透传。
     * 上层（urana → process → ai）经本方法取得缓冲，agent 据此创建共享 feelingBuffer。
     * 未实现的 mapper 走默认（抛异常）——组装非法组合时 fail-fast。
     */
    default VectorBase newFeelingBuffer() {
        throw new UnsupportedOperationException("此映射器未发布感觉载体契约（newFeelingBuffer）");
    }

    /**
     * 创建视觉解码器（可选能力，default 委托链的 mapper 段）。
     * <p>
     * 解码器由映射器直接创建（映射器是解码器的模式，解码器住映射器包），
     * 与感觉载体配对。上层经本方法取得解码器，agent 注入感受器。
     */
    default VisionEncoder newVisionEncoder() {
        throw new UnsupportedOperationException("此映射器未发布视觉解码器（newVisionEncoder）");
    }

    /**
     * 创建行为缓冲区（可选能力，default 委托链的 mapper 段）。
     * 长度取自本 mapper 的 outputDomain behavior span，载体类型由所持 nn 家族决定。
     */
    default VectorBase newBehaviorBuffer() {
        throw new UnsupportedOperationException("此映射器未发布行为载体契约（newBehaviorBuffer）");
    }

    /**
     * 行为读取（可选能力，default 委托链的 mapper 段）。
     * 把行为缓冲区载体数据读出为 int[]（effector 期望的 bit-packed 格式）。
     */
    default void readBehaviorTo(VectorBase behaviorBuffer, int[] dst, long stream) {
        throw new UnsupportedOperationException("此映射器未发布行为读取契约（readBehaviorTo）");
    }
}