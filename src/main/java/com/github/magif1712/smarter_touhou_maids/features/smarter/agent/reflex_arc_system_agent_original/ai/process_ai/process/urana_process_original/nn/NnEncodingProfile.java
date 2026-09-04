package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn;

/**
 * nn 载体的编码剖面（纯数据）。
 * <p>
 * 描述 urana 各语义对象（F/B/dt/G）被 nn 载体编码成多少个载体元素。
 * <p>
 * <b>设计原则（真善美第2条）</b>：urana 意识域 C 只有"语义对象 C/F/B/G/dt 的存在、
 * 相对位置 C@F@G@dt / C@F@B、数量关系 C=F×3、G 有 4 种方位、dt 是一个 long"。
 * C 中<b>没有</b>"每个对象编码成多少载体元素"——后者是 nn 载体编码的模式，
 * 由本 profile 提供。把具体编码长度从 urana 的 {@code InputVectorDomain}/{@code OutputVectorDomain}
 * 下沉到 nn，使 D 中不再有 C 中没有的模式。
 * <p>
 * <b>设计原则（真善美第3条）</b>：把"长度由 nn 决定"这个不实在约束，用纯数据类 + factory/实例方法
 * 实在化。换 nn 实现（BNN→CNN）时，新实现返回自己的 profile（float 维度），
 * urana 的 domain/factory/GradCell/InferenceCell 零改动。
 * <p>
 * <b>两个查询点</b>（破鸡生蛋）：
 * <ul>
 *   <li>{@link NnFactory#encodingProfile()}：factory 级，创建 nn 实例<b>之前</b>查询
 *       （算 inputSize/outputSize 需要 profile，而 nn 实例尚未创建）；</li>
 *   <li>{@link INeuralNetwork#encodingProfile()}：实例级，持 nn 引用的组件
 *       （AbstractGradCell/AbstractInferenceCell/UranaSystem）建 domain 时查询。</li>
 * </ul>
 * 两者返回同一个值（nn 实现类持静态 profile 常量，factory 与实例方法均返回它）。
 * <p>
 * <b>不泄露语义给 nn</b>：本 profile 只描述"各对象编码多长"，不告诉 nn "哪段是 F/B/C/dt"——
 * span 仍由 urana 传入，nn 仍不感知层向量的区间语义（与 INeuralNetwork 既有契约一致）。
 *
 * @see NnFactory#encodingProfile()
 * @see INeuralNetwork#encodingProfile()
 */
public final class NnEncodingProfile {
    /** F 感觉的编码长度（BNN: 1920×1080×24 位平面；CNN 可能: float 维度）。 */
    public final int feelingLength;
    /** B 行为的编码长度（BNN: 256 行为位；CNN 可能: float 维度）。 */
    public final int behaviorLength;
    /** dt 的编码长度（BNN: 64 = long 拆 2×uint32 word；CNN 可能: 1 float）。 */
    public final int dtLength;
    /** G 单分量的编码长度（BNN: 1 bit one-hot；CNN 可能: 1 float）。urana 知道 G 有 4 个分量。 */
    public final int timeOrientationUnitLength;

    public NnEncodingProfile(int feelingLength, int behaviorLength,
                             int dtLength, int timeOrientationUnitLength) {
        this.feelingLength = feelingLength;
        this.behaviorLength = behaviorLength;
        this.dtLength = dtLength;
        this.timeOrientationUnitLength = timeOrientationUnitLength;
    }
}
