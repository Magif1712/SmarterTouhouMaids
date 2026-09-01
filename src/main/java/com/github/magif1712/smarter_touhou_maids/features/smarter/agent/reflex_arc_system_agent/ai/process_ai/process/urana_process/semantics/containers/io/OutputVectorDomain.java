package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.NnEncodingProfile;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.BehaviorSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.FeelingSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.InheritanceInfoSpan;

/**
 * 输出向量域：按 profile 把输出向量实在化为 C@F@B 三段布局（真善美第4条）。
 * <p>
 * 布局顺序：继承信息 C → feeling F → behavior B。
 * <p>
 * 设计原则（真善美第3条）：布局长度取自 profile（换 nn 时跟着换），urana 不感知具体长度。
 */
public class OutputVectorDomain {
    public static final int INHERITANCE_MULTIPLIER = 3;

    private final int totalLength;
    private final InheritanceInfoSpan inheritanceInfoSpan;
    private final FeelingSpan feelingSpan;
    private final BehaviorSpan behaviorSpan;

    public OutputVectorDomain(NnEncodingProfile profile) {
        int cLen = profile.getFeelingLength() * INHERITANCE_MULTIPLIER;
        int fLen = profile.getFeelingLength();
        int bLen = profile.getBehaviorLength();
        this.totalLength = cLen + fLen + bLen;

        int currentOffset = 0;
        this.inheritanceInfoSpan = new InheritanceInfoSpan(currentOffset, cLen);
        currentOffset += cLen;
        this.feelingSpan = new FeelingSpan(currentOffset, fLen);
        currentOffset += fLen;
        this.behaviorSpan = new BehaviorSpan(currentOffset, bLen);
    }

    public int totalLength() {
        return totalLength;
    }

    public FeelingSpan getFeelingSpan() {
        return feelingSpan;
    }

    public BehaviorSpan getBehaviorSpan() {
        return behaviorSpan;
    }

    public InheritanceInfoSpan getInheritanceInfoSpan() {
        return inheritanceInfoSpan;
    }
}
