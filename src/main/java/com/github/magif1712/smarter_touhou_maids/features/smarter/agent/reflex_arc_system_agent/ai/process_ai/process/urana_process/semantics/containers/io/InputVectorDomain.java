package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.NnEncodingProfile;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.FeelingBehaviorSamplingDtSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.FeelingSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.InheritanceInfoSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.TargetTimeOrientationSpan;

/**
 * 输入向量域：按 profile 把输入向量实在化为 C@F@G@dt 四段布局（真善美第4条）。
 * <p>
 * 布局顺序：继承信息 C → feeling F → 时间方位 G → 采样时间间隔 dt。
 * <p>
 * 设计原则（真善美第3条）：布局长度取自 profile（换 nn 时跟着换），urana 不感知具体长度。
 */
public class InputVectorDomain {
    public static final int INHERITANCE_MULTIPLIER = 3;
    public static final int TIME_ORIENTATION_COUNT = 4;

    private final int totalLength;
    private final InheritanceInfoSpan inheritanceInfoSpan;
    private final FeelingSpan feelingSpan;
    private final TargetTimeOrientationSpan targetTimeOrientationSpan;
    private final FeelingBehaviorSamplingDtSpan feelingBehaviorSamplingDtSpan;

    public InputVectorDomain(NnEncodingProfile profile) {
        int cLen = profile.getFeelingLength() * INHERITANCE_MULTIPLIER;
        int fLen = profile.getFeelingLength();
        int gLen = profile.getTimeOrientationUnitLength() * TIME_ORIENTATION_COUNT;
        int dtLen = profile.getDtLength();
        this.totalLength = cLen + fLen + gLen + dtLen;

        int currentOffset = 0;
        this.inheritanceInfoSpan = new InheritanceInfoSpan(currentOffset, cLen);
        currentOffset += cLen;
        this.feelingSpan = new FeelingSpan(currentOffset, fLen);
        currentOffset += fLen;
        this.targetTimeOrientationSpan = new TargetTimeOrientationSpan(currentOffset, gLen);
        currentOffset += gLen;
        this.feelingBehaviorSamplingDtSpan = new FeelingBehaviorSamplingDtSpan(currentOffset, dtLen);
    }

    public int totalLength() {
        return totalLength;
    }

    public FeelingSpan getFeelingSpan() {
        return feelingSpan;
    }

    public FeelingBehaviorSamplingDtSpan getFeelingBehaviorSamplingDtSpan() {
        return feelingBehaviorSamplingDtSpan;
    }

    public InheritanceInfoSpan getInheritanceInfoSpan() {
        return inheritanceInfoSpan;
    }

    public TargetTimeOrientationSpan getTargetTimeOrientationSpan() {
        return targetTimeOrientationSpan;
    }
}
