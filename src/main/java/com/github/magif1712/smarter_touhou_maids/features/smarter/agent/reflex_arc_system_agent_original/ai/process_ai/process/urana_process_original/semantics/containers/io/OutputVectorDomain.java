package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.semantics.containers.io;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Domain;
import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.NnEncodingProfile;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.semantics.containers.io.subspan.BehaviorSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.semantics.containers.io.subspan.FeelingSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.semantics.containers.io.subspan.InheritanceInfoSpan;

/**
 * OutputVector 的语义布局描述符。
 * <p>
 * 纯粹的、无状态的域描述符：将逻辑输出向量分割为有语义的子域（C/F/B）。
 * 不持底层数据容器引用，无数据操作方法。
 * <p>
 * <b>设计原则（真善美第2条）</b>：urana 意识域 C 只有"语义对象 + 相对位置 + 数量关系"。
 * 本类只保留 C 中的模式：
 * <ul>
 *   <li>布局顺序 C@F@B（构造里按此顺序排 span offset）；</li>
 *   <li>数量关系 C=F×3（{@link #INHERITANCE_MULTIPLIER_original}）。</li>
 * </ul>
 * C 中没有"各对象编码成多少载体元素"——该长度由 {@link NnEncodingProfile} 提供（nn 载体编码的属性）。
 * <p>
 * <b>设计原则（真善美第3条）</b>：构造收 profile，span 长度 = profile 基础长度 × urana 倍数关系。
 * 换 nn 实现（BNN→CNN）时，新 factory 返回自己的 profile，本类零改动。
 * <p>
 * <b>曾是 static 常量</b>：原 FEELING_SPAN_LENGTH=1920*1080*24 / BEHAVIOR_SPAN_LENGTH=256 等
 * 硬编码已下沉到 {@code AbstractBnnNeuralNetwork.BNN_PROFILE_original}（搬家不增减）。
 */
public class OutputVectorDomain extends Domain<Span> {
    // === urana 意识域不变量（不随 nn 变）===
    /** C = F × 3：传承信息承载过去/现在/未来三态感觉。 */
    private static final int INHERITANCE_MULTIPLIER_original = 3;

    // 结构: C, F, B（布局顺序是 urana 的，长度项由 profile 提供）
    private final FeelingSpan feelingSpan_original;
    private final BehaviorSpan behaviorSpan_original;
    private final InheritanceInfoSpan inheritanceInfoSpan_original;
    private final int totalLength_original;

    public OutputVectorDomain(NnEncodingProfile profile) {
        int cLen = profile.feelingLength * INHERITANCE_MULTIPLIER_original;
        int fLen = profile.feelingLength;
        int bLen = profile.behaviorLength;
        this.totalLength_original = cLen + fLen + bLen;

        int currentOffset = 0;
        // C: 系统状态层（最稳定）
        this.inheritanceInfoSpan_original = new InheritanceInfoSpan(currentOffset, cLen);
        currentOffset += cLen;
        // F: 感知反馈层（实时更新）
        this.feelingSpan_original = new FeelingSpan(currentOffset, fLen);
        currentOffset += fLen;
        // B: 行为输出层（微小数据）
        this.behaviorSpan_original = new BehaviorSpan(currentOffset, bLen);
    }

    /** 输出向量总长度 = C+F+B。供 UranaProcessFactory 算 outputSize 传给 nnFactory.create。 */
    public int totalLength() {
        return totalLength_original;
    }

    public FeelingSpan getFeelingSpan() {
        return feelingSpan_original;
    }

    public BehaviorSpan getBehaviorSpan() {
        return behaviorSpan_original;
    }

    public InheritanceInfoSpan getInheritanceInfoSpan() {
        return inheritanceInfoSpan_original;
    }

    @Override
    public boolean contains(Span element) {
        return element == feelingSpan_original ||
                element == behaviorSpan_original ||
                element == inheritanceInfoSpan_original;
    }
}
