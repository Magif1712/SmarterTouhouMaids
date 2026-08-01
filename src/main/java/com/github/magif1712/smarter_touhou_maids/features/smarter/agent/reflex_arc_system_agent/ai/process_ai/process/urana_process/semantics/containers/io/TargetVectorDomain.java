package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Domain;
import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.BehaviorSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.FeelingSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.InheritanceInfoSpan;

/**
 * TargetVector 的语义布局描述符。
 * <p>
 * 这个类是一个纯粹的、无状态的域描述符。
 * 它定义了目标向量的逻辑长度，与输出向量的长度保持一致。
 * 它不持有任何对底层数据容器的引用。
 */
public class TargetVectorDomain extends Domain<Span> {
    // --- 子域长度定义 ---
    public static final int FEELING_SPAN_LENGTH = 64;
    public static final int BEHAVIOR_SPAN_LENGTH = 64;
    public static final int INHERITANCE_INFO_SPAN_LENGTH = 8;

    public static final int TOTAL_LENGTH = FEELING_SPAN_LENGTH +
            BEHAVIOR_SPAN_LENGTH +
            INHERITANCE_INFO_SPAN_LENGTH;

    private final FeelingSpan feelingSpan;
    private final BehaviorSpan behaviorSpan;
    private final InheritanceInfoSpan inheritanceInfoSpan;

    public TargetVectorDomain() {
        int currentOffset = 0;
        this.feelingSpan = new FeelingSpan(currentOffset, FEELING_SPAN_LENGTH);
        currentOffset += FEELING_SPAN_LENGTH;

        this.behaviorSpan = new BehaviorSpan(currentOffset, BEHAVIOR_SPAN_LENGTH);
        currentOffset += BEHAVIOR_SPAN_LENGTH;

        this.inheritanceInfoSpan = new InheritanceInfoSpan(currentOffset, INHERITANCE_INFO_SPAN_LENGTH);
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

    @Override
    public boolean contains(Span element) {
        return element == feelingSpan ||
                element == behaviorSpan ||
                element == inheritanceInfoSpan;
    }
}