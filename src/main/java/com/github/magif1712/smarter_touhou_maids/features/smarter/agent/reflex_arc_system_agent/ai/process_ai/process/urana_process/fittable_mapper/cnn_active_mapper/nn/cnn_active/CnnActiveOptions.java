package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper.nn.cnn_active;

/**
 * 新活性 CNN 的分解方式 {@code D}（方案 §五 裁决1：把 {@code D} 实在化为显式函数入参）。
 * <p>
 * <b>为什么需要它</b>（推论1 方案A）：原 CNN 把"用哪种激活、权重初始化多大"这些设计选择<b>写死在代码里</b>，
 * 上层无法选择——即 {@code Ext(D) = ∅}。本类把 {@code D} 上推为显式入参，使"这次选哪套分解方式"
 * 成为上层界面（{@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamPanelProvider}）
 * 的可见选项。描述长度 {@code O(k)}，{@code k} = 合法分解数 = 2 ✓。
 * <p>
 * <b>暴露的 {@code Ext(D)}</b>（推论2：用"结构不存在"来裁剪）：
 * <pre>
 *   Ext(D) = { (activationId, initBoundW, initBoundB) | activationId ∈ {1,2}, boundW > 0, boundB > 0 }
 * </pre>
 * 刻意<b>不暴露</b>的三项：
 * <ul>
 *   <li>{@code sigmoid}——不存在于 {@code Ext(D激活)}：值域 {@code (0,1)} 无法表示有符号的 C 段 target
 *       （C 段占输出 75%，其 target 是入参梯度 {@code dx}），对负 target 恒有 {@code y > t} ⟹ δ 恒正
 *       ⟹ 权重被单向推 ⟹ 结构性学不动。这是值域不匹配 ⇒ <b>非法</b>，不是"不推荐"。</li>
 *   <li>{@code threshold}——不存在：两种合法激活共用 {@code θ = 0}（{@code tanh(0)=0}、{@code clip(0)=0}），
 *       故 θ 退化为结构常量 {@link #THRESHOLD}，不可独立组合。</li>
 *   <li>{@code p} 的初始化符号域——结构性禁止：{@code p} 是<b>位置</b>（{@code [0, sizeA1)}），
 *       对称化会把位置打到负数。{@code p} 恒定走原 {@code fillRandom} 语义。</li>
 * </ul>
 * 因为初始化<b>恒为对称</b>、激活<b>恒为奇</b>，"σ × 全非负初始化"与"σ × 有符号 target"这两类病态组合
 * <b>在结构上不可能被选出</b>。
 * <p>
 * <b>运行期稳定不变量（I3/I4，L0.2）</b>：{@code Ext(D)} 之外还有一组**不可选**的约束——权重限幅
 * （{@code |q|,|l|,|r|,|b| ≤ W}）与 B 段自环收缩（{@code |l_j|+|r_j| ≤ cap < 1}）。它们与
 * {@code THRESHOLD} 同属"结构常量"（不暴露、不设第二读路径）；其数值的权威定义在 native
 * （{@code gradient_ops.cu}），本类的字段块注释仅作索引——理由见该注释（避免双副本静默漂移）。
 * <p>
 * <b>不可变 + 构造期校验</b>（真善美第4条）：把"哪些分解方式合法"这个不实在约束，实在化为
 * final 字段 + 构造期 fail-fast。非法值不可能进入运行期（违反者立刻暴露，而非静默传播）。
 * <p>
 * <b>判决的符号不变性</b>（方案 §2.1 命题）：{@code sigmoid}(θ=0.5)、{@code tanh}(θ=0)、
 * {@code clipped-linear}(θ=0) 三者的判决<b>逐位等价</b>，都等价于 {@code [z ≥ 0]}——
 * 因为三者都严格单调、过零（σ(0)=0.5 ⟹ σ(z)≥0.5 ⟺ z≥0）。故选激活的依据是<b>值域（真）</b>
 * 与<b>梯度（善）</b>，不是"活性"。
 */
public final class CnnActiveOptions {

    /** {@code activationId = 1}：tanh（默认）。奇对称、{@code C∞}、导数是 {@code 1 − y²}（无需 z）、软饱和还能学。 */
    public static final int ACTIVATION_TANH = 1;
    /** {@code activationId = 2}：clipped-linear。奇对称，但 {@code |z| ≥ 1} 处导数是硬零 ⟹ 该单元永久不学，仅作可选。 */
    public static final int ACTIVATION_CLIPPED_LINEAR = 2;

    /**
     * 判决阈值（结构常量，不暴露）。
     * <p>
     * 两种合法激活都过零 ⟹ {@code a(z) ≥ 0 ⟺ z ≥ 0} ⟹ θ 恒为 0，无独立选择空间。
     */
    public static final float THRESHOLD = 0.0f;

    /** {@code initBoundW}/{@code initBoundB} 的默认半径（方案 §3.1 量级论证：{@code z ≈ (−1.5, 1.5)}）。 */
    public static final float DEFAULT_BOUND = 0.5f;

    /*
     * ─────────────── L0.2 运行期稳定不变量（I3 / I4）——本类只作文档索引，不持数值 ───────────────
     *
     * I3 权重限幅（|q|,|l|,|r|,|b| ≤ W）与 I4 B 段自环收缩（|l_j|+|r_j| ≤ cap < 1，j 属 B 段）
     * 是运行期的**结构常量**，与上面的 THRESHOLD 同构（方案 §5.1 裁决 (b)）：若做成 Ext(D) 的可选参数，
     * 按推论2 必须把含发散组合的取值裁掉，裁完等价于"不可选"，却白白多出滑块 ⇒ 熵增（公理(3)）。
     * 故做成**不变量**（不可选、不进 GUI），并**退化为结构常量**（不暴露、不设第二读路径）。
     *
     * 【为什么不在这里声明数值】对同一常量保留两份数值副本会**可静默漂移**——违反真善美第1条"真"
     * 与第3条"单一数据源"。而 native 热路径无法在编译期读到 Java 常量，为其加 JNI 参数又会破坏
     * "零签名变化"。故：**数值的权威定义唯一放在 native**
     * （native/.../cnn_active/mapping/gradient/gradient_ops.cu 的 WEIGHT_LIMIT / LOOP_GAIN_CAP /
     *  cnn_active_clamp_weight），本处只作设计文档索引。L0.3 若诊断面板需显示该界限，
     * 应另加 native 取值函数，而非复制常量。
     *
     * 参考取值（**以 native 为准**，此处仅为阅读便利复述，不参与任何控制流）：
     *   WEIGHT_LIMIT  = 4.0f  —— 取 = CnnActiveMapperFactory.MAX_BOUND，使初始化滑块全域在运行期可达
     *                           （方案 §5.3 选项乙：滑块管初始化、不变量管可行集）；
     *   LOOP_GAIN_CAP = 0.9f  —— ε = 0.1（方案 §5.4）；必须 < 1 才使 B 段递归为收缩映射
     *                           （诊断报告 §3.3：该增益中位由 0.50 漂到 1.14＝发散主因之一）。
     * 精确数值属 L1 采样标定范畴；L0 只主张"约束的形式必须存在"（方案 §5.4）。
     */

    private final int activationId;
    private final float initBoundW;
    private final float initBoundB;

    /**
     * 构造分解方式 D。所有约束在构造期校验（fail-fast），非法值抛 {@link IllegalArgumentException}。
     *
     * @param activationId 激活 id，只能是 {@link #ACTIVATION_TANH} 或 {@link #ACTIVATION_CLIPPED_LINEAR}
     * @param initBoundW   {@code q/l/r} 的对称初始化半径，必须 {@code > 0}
     * @param initBoundB   {@code b} 的对称初始化半径，必须 {@code > 0}
     */
    public CnnActiveOptions(int activationId, float initBoundW, float initBoundB) {
        if (activationId != ACTIVATION_TANH && activationId != ACTIVATION_CLIPPED_LINEAR) {
            throw new IllegalArgumentException(
                    "activationId 必须为 " + ACTIVATION_TANH + "(tanh) 或 " + ACTIVATION_CLIPPED_LINEAR
                            + "(clipped-linear)；sigmoid 不在 Ext(D) 内（值域不匹配有符号 target）。收到: " + activationId);
        }
        if (!(initBoundW > 0.0f)) {
            throw new IllegalArgumentException("initBoundW 必须 > 0，收到: " + initBoundW);
        }
        if (!(initBoundB > 0.0f)) {
            throw new IllegalArgumentException("initBoundB 必须 > 0，收到: " + initBoundB);
        }
        this.activationId = activationId;
        this.initBoundW = initBoundW;
        this.initBoundB = initBoundB;
    }

    /** 默认分解方式：tanh + 半径 0.5/0.5（即 L0）。 */
    public static CnnActiveOptions defaults() {
        return new CnnActiveOptions(ACTIVATION_TANH, DEFAULT_BOUND, DEFAULT_BOUND);
    }

    public int getActivationId() {
        return activationId;
    }

    public float getInitBoundW() {
        return initBoundW;
    }

    public float getInitBoundB() {
        return initBoundB;
    }
}
