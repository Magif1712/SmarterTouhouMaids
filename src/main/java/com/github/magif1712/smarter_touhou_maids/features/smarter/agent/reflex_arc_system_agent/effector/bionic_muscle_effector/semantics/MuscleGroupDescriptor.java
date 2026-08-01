package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.bionic_muscle_effector.semantics;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;

/**
 * 肌群拓扑描述符：身份 + 位段。
 * <p>
 * 对标 {@code OutputVectorDomain} 的子域 Span（如 FeelingSpan/BehaviorSpan），
 * 但采用组合（has-a）而非继承（is-a）——因为肌群“有”一个位段，不“是”位段。
 * <p>
 * 不区分 DISCRETE/CONTINUOUS：生物上肌肉全是连续收缩的，
 * 离散/连续的区分是“执行层”的判决模式（阈值化），不是语义层的属性。
 * 所有肌群统一输出连续激活强度 [0,1]，由执行层决定如何阈值化。
 */
public class MuscleGroupDescriptor {

    private final MuscleGroupId id;
    private final Span span;

    public MuscleGroupDescriptor(MuscleGroupId id, int offset, int length) {
        this.id = id;
        this.span = new Span(offset, length) {};
    }

    public MuscleGroupId getId() {
        return id;
    }

    public Span getSpan() {
        return span;
    }

    public int getOffset() {
        return span.getOffset();
    }

    public int getLength() {
        return span.getLength();
    }
}
