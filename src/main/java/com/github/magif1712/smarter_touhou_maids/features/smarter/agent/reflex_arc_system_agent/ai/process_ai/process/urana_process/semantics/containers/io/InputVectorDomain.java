package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Domain;
import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.FeelingBehaviorSamplingDtSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.FeelingSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.InheritanceInfoSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.TargetTimeOrientationSpan;

/**
 * InputVector 的语义布局描述符。
 * <p>
 * 这个类是一个纯粹的、无状态的域描述符。它只知道如何将一个逻辑上的输入向量
 * 分割为多个具有语义的子域（Span），如“感觉”、“目标时间”等。
 * 它不持有任何对底层数据容器的引用，也没有任何数据操作方法。
 */
public class InputVectorDomain extends Domain<Span> {
    // --- 子域长度定义 ---
    // 结构: C, F, G, dt
    public static final int FEELING_SPAN_LENGTH = 1920 * 1080 * 24; // F
    public static final int TARGET_TIME_ORIENTATION_SPAN_LENGTH = 4; // G
    public static final int FEELING_BEHAVIOR_SAMPLING_DT_SPAN_LENGTH = 64; // dt
    public static final int INHERITANCE_INFO_SPAN_LENGTH = FEELING_SPAN_LENGTH * 3; // C

    public static final int TOTAL_LENGTH = FEELING_SPAN_LENGTH +
            TARGET_TIME_ORIENTATION_SPAN_LENGTH +
            FEELING_BEHAVIOR_SAMPLING_DT_SPAN_LENGTH +
            INHERITANCE_INFO_SPAN_LENGTH;

    private final FeelingSpan feelingSpan; // F
    private final TargetTimeOrientationSpan targetTimeOrientationSpan; // G
    private final FeelingBehaviorSamplingDtSpan feelingBehaviorSamplingDtSpan; // dt
    private final InheritanceInfoSpan inheritanceInfoSpan; // C

    public InputVectorDomain() {
        int currentOffset = 0;

        // C: 系统状态层（最稳定）
        this.inheritanceInfoSpan = new InheritanceInfoSpan(currentOffset, INHERITANCE_INFO_SPAN_LENGTH);
        currentOffset += INHERITANCE_INFO_SPAN_LENGTH;

        // F: 感知输入层（实时更新）
        this.feelingSpan = new FeelingSpan(currentOffset, FEELING_SPAN_LENGTH);
        currentOffset += FEELING_SPAN_LENGTH;

        // G: 辅助参数层（微小数据）
        this.targetTimeOrientationSpan = new TargetTimeOrientationSpan(currentOffset,
                TARGET_TIME_ORIENTATION_SPAN_LENGTH);
        currentOffset += TARGET_TIME_ORIENTATION_SPAN_LENGTH;

        // dt: 辅助参数层（微小数据）
        this.feelingBehaviorSamplingDtSpan = new FeelingBehaviorSamplingDtSpan(currentOffset,
                FEELING_BEHAVIOR_SAMPLING_DT_SPAN_LENGTH);
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

    @Override
    public boolean contains(Span element) {
        return element == feelingSpan ||
                element == targetTimeOrientationSpan ||
                element == feelingBehaviorSamplingDtSpan ||
                element == inheritanceInfoSpan;
    }
}