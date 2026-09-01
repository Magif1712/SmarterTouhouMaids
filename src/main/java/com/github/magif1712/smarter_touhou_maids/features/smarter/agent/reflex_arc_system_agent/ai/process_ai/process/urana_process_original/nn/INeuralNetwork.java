package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;

/**
 * 神经网络的机械级抽象边界。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：urana 意识域 C 只有"节律/流程/语义对象(F·B·C·dt·G)/锚点/痕迹/工作记忆"模式，
 *       不含 BNN 的权重/梯度/forward/backward 等模式。这些 nn 模式必须藏在实现里，
 *       urana 通过本接口访问。换 NN 实现时 urana 零改动。</li>
 *   <li><b>第3条</b>：把"可替换 NN"这个不实在的约束，用实在的接口（有签名的方法）固化。
 *       urana 持本接口引用，nn 实现可替换。</li>
 * </ul>
 * <p>
 * <b>语义归属</b>：本接口只暴露机械操作（区域读写、前向反向、向量工厂），
 * <b>不知道</b>层向量的区间有什么语义（哪段是 F/B/C/dt/G）——span 由 urana 传入。
 * dt 的时间语义也在 urana：urana 调 {@link #copyToInputFromLong} 传入 dtSpan+dtMillis，
 * 本接口只做"long 值编码进我的载体格式填到指定 region"的机械动作。
 *
 * @see com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.AbstractBnnNeuralNetwork
 */
public interface INeuralNetwork extends AutoCloseable {

    // ==================== IO 区域读写 ====================
    // nn 模式：封装"输入/输出向量的区域写入/读出"，提升可读性（与 IVector 多余不同——此处对应 nn 真实模式）。
    // 内部委托 VectorBase.copyRegionFrom，但表达的是"nn 输入/输出区域"这个 nn 概念。

    /**
     * 把 src 全量拷贝到 nn 输入向量的指定 region。
     *
     * @param region nn 输入向量的目标区段（由 urana 指定，如 feelingSpan/dtSpan/GSpan）。
     * @param src    源向量（urana 侧的 F/C/dt 等状态，载体类型由 nn 决定）。
     */
    void copyToInput(Span region, VectorBase src, long stream);

    /**
     * 把 nn 输入向量的指定 region 拷贝到 dst 全量（用于 aPrev 快照）。
     */
    void copyFromInput(Span region, VectorBase dst, long stream);

    /**
     * 把 nn 输出向量的指定 region 拷贝到 dst 全量（用于 urana 取 F_out/C_out/B）。
     */
    void copyFromOutput(Span region, VectorBase dst, long stream);

    /**
     * 从 host boolean[] 拷贝到 nn 输入向量的指定 region（G 向量专用）。
     */
    void copyToInputFromHost(Span region, boolean[] src, long stream);

    /**
     * 把 long 值编码进 nn 载体格式，填到 nn 输入向量的指定 region。
     * <p>
     * dt 语义归属 urana：urana 传 (dtSpan, dtMillis)——它知道"这个 span 是 dt、这个 long 是时间间隔"。
     * 本方法只做机械的"long→载体格式→填 region"，不知道 dt 含义。
     * BNN 实现：long 拆 2 个 uint32 word 写入 BoolVector；CNN 实现：可能编码成 float。
     *
     * @param region nn 输入向量的目标区段（urana 侧传入 dtSpan）。
     * @param value  待编码的 long 值（urana 侧传入 dtMillis）。
     */
    void copyToInputFromLong(Span region, long value, long stream);

    // ==================== 前向 / 反向 ====================

    /**
     * 推理前向（不存 fz）。urana 适配器推理链调用。
     */
    void forward(long stream);

    /**
     * 训练前向，中间态写入 fz（供反向使用）。urana 适配器训练链调用。
     *
     * @param fz 前向中间态缓存（载体类型由 nn 决定，BNN 是 BoolVector）；由 urana 适配器持有、nn 创建。
     */
    void forwardForTraining(VectorBase fz, long stream);

    /**
     * 反向传播（不更新权重）。urana 适配器训练链"探索阶段"调用。
     */
    void backward(VectorBase fz, long stream);

    /**
     * 反向传播 + 梯度下降更新权重。urana 适配器训练链"修正阶段"调用。
     *
     * @param aPrev 前向输入快照（由 urana 适配器在前向前用 copyFromInput 拷出持有）。
     */
    void backwardAndUpdate(VectorBase fz, VectorBase aPrev, long stream);

    // ==================== 目标与梯度 ====================

    /**
     * 填 nn 内部 target 向量的指定 region。nn 内部持 target，setTarget 填它。
     */
    void setTarget(Span region, VectorBase src, long stream);

    /**
     * 计算 nn 输出层梯度（基于内部 target 与给定 currentOutput 的差）。
     *
     * @param currentOutput 当前步骤的输出向量（urana 适配器持有的 state.output）。
     * @param region        要计算梯度的子区间（urana 传 feelingSpan/behaviorSpan）。
     */
    void computeOutputGradient(VectorBase currentOutput, Span region, long stream);

    /**
     * 把 ∇C 注入 nn 输出层梯度的指定 region（链式 BPTT 跨步梯度传递）。
     */
    void injectOutputGradient(Span region, VectorBase gradC, long stream);

    /**
     * 把 nn 输入层梯度的指定 region 拷贝到 dst（取 ∇C 供下一步反向）。
     */
    void copyFromInputGradient(Span region, VectorBase dst, long stream);

    /**
     * 把梯度向量（∇C）转换成输入 C 向量（C'），供训练阶段二作为修正后的初始上下文。
     * <p>
     * <b>BNN 特定</b>：negateAndBinarize（IntVector 梯度 → BoolVector 输入）。
     * CNN 实现可能是 negate 或直接用。这是"梯度→输入"的 nn 特定转换，藏在实现里。
     * <p>
     * "阶段二用修正后的 C' 前向"是通用训练模式（留 urana 编排）；
     * "C' 如何从 ∇C 算出来"是 nn 特定（藏 nn 实现）。
     *
     * @param gradC   源梯度向量（urana 适配器持有的 grad_C_buffer，nn.createGradientVector 创建）。
     * @param inputC  目标输入 C 向量（urana 适配器持有的 c_input_for_phase2，nn.createVector 创建）。
     */
    void gradientToInput(VectorBase gradC, VectorBase inputC, long stream);

    /**
     * 把 nn 内部输入层梯度的指定 region 直接拷贝到 nn 内部输出层梯度的同一 region。
     * <p>
     * 用于链式 BPTT 非终端步的 ∇C_out 注入：瞬态组内链条梯度不落 urana 缓冲区，
     * 直接在 nn 内部从输入层梯度区间搬到输出层梯度区间。
     * <p>
     * 与 {@link #injectOutputGradient} 的区别：本方法的源是 nn 内部输入层梯度（瞬态，无 urana 缓冲区），
     * 后者的源是 urana 持有的外部缓冲区（跨轮持存 T_prev）。
     *
     * @param region 要搬运的区段（urana 传 cSpan_out）。
     */
    void injectOutputGradientFromInputGradient(Span region, long stream);

    /**
     * 把 nn 内部输入层梯度的指定 region 经 nn 特定变换转为输入 C 向量（C'），供阶段二前向使用。
     * <p>
     * 用于跨阶段 S1→C' 转换：瞬态 S1（阶段一反向算出的输入层梯度的 C 部分）不落 urana 缓冲区，
     * 直接读 nn 内部输入层梯度。与 {@link #gradientToInput} 的区别：本方法的源是 nn 内部输入层梯度，
     * 后者的源是 urana 持有的外部梯度向量。
     * <p>
     * <b>BNN 特定</b>：negateAndBinarize 的区间版（IntVector 梯度 region → BoolVector 输入）。
     *
     * @param region 输入层梯度中的 C 区段（urana 传 cSpan_out）。
     * @param inputC 目标输入 C 向量（urana 持有的 c_input_for_phase2，nn.createVector 创建）。
     */
    void gradientToInputFromInternal(Span region, VectorBase inputC, long stream);

    /**
     * 把梯度向量清零（标量乘 0）。
     * <p>
     * BNN: IntVector.multiplyByScalar(0)；CNN: FloatVector 等价操作。
     * urana 适配器首轮初始化 grad_C_buffer 为零时调用。
     *
     * @param gradVec 梯度向量（nn.createGradientVector 创建）。
     */
    void zeroGradient(VectorBase gradVec, long stream);

    /**
     * 把数据向量清零（全位/全值置 0）。
     * <p>
     * BNN: BoolVector 用 copyRegionFromHost(false[])；CNN: FloatVector 用 fill(0) 等价。
     * urana 适配器初始化 zero_vector_C 时调用。与 zeroGradient 区别：本方法针对数据向量（createVector 创建），
     * zeroGradient 针对梯度向量（createGradientVector 创建）——两类载体清零路径不同。
     *
     * @param vec 数据向量（nn.createVector 创建）。
     */
    void zeroVector(VectorBase vec, long stream);

    // ==================== 向量工厂 ====================

    /**
     * 创建一个数据/前向中间态向量（载体类型由 nn 决定）。
     * urana 用它创建状态向量（prospectiveInheritance/traceFeeling/fz/state.input/output 等）。
     */
    VectorBase createVector(int size);

    /**
     * 创建一个梯度向量（载体类型由 nn 决定，BNN 是 IntVector，CNN 可能是 FloatVector）。
     * urana 用它创建 grad_C_buffer。
     */
    VectorBase createGradientVector(int size);

    // ==================== 序列化 ====================

    /**
     * 将网络权重序列化到磁盘。
     */
    void save(String folderPath);

    /**
     * 从磁盘加载数据向量到新实例（与 {@link #createVector} 对称：nn 知载体类型）。
     * <p>
     * BNN 用 {@link com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector#loadFromFile}；
     * CNN 用等价 FloatVector load。调用方负责将数据拷入已有缓冲区并关闭临时实例。
     * <p>
     * <b>load/save 对称</b>（C2/C3）：save 由实例写磁盘，load 由 nn 知载体类型造新实例——
     * urana 持 VectorBase 引用能 save（多态），但 load 不知子类，故需 nn 提供对称 load 接口。
     *
     * @param path 文件路径
     * @return 新加载的向量实例（调用方负责关闭）
     */
    VectorBase loadVector(String path);

    /**
     * 从磁盘加载梯度向量到新实例（与 {@link #createGradientVector} 对称）。
     * <p>
     * BNN 用 {@link com.github.magif1712.smarter_touhou_maids.core.containers.vector.IntVector#loadFromFile}。
     *
     * @param path 文件路径
     * @return 新加载的梯度向量实例（调用方负责关闭）
     */
    VectorBase loadGradientVector(String path);

    // ==================== 编码剖面 ====================

    /**
     * 返回本 nn 载体的编码剖面（各语义对象 F/B/dt/G 的载体编码长度）。
     * <p>
     * 设计原则（真善美第2条）：urana 意识域 C 只有"语义对象 + 相对位置 + 数量关系(C=F×3, G=4方位)"，
     * 没有"每个对象编码成多少载体元素"。后者是 nn 载体编码的模式，由本方法提供。
     * <p>
     * 设计原则（真善美第3条）：把"长度由 nn 决定"这个不实在约束，用实例方法实在化。
     * 换 nn 实现（BNN→CNN）时，新实现返回自己的 profile，urana 的 domain/factory 零改动。
     * <p>
     * <b>与 {@link NnFactory#encodingProfile()} 对称</b>：factory 级方法供实例化前查询
     * （算 inputSize 需要 profile，而 nn 实例未创建）；本实例方法供持 nn 引用的组件
     * （AbstractGradCell/AbstractInferenceCell/UranaSystem）建 domain 时查询。两者返回同一个值。
     *
     * @return 本 nn 的编码剖面
     */
    NnEncodingProfile encodingProfile();
}
