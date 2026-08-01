package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Domain;
import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.BehaviorSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.FeelingSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.InheritanceInfoSpan;

/**
 * OutputVector 的语义布局描述符。
 * <p>
 * 这个类是一个纯粹的、无状态的域描述符。它只知道如何将一个逻辑上的输出向量
 * 分割为多个具有语义的子域（Span），如"感觉"、"行为"等。
 * 它不持有任何对底层数据容器的引用，也没有任何数据操作方法。
 */
public class OutputVectorDomain extends Domain<Span> {
    // --- 子域长度定义 ---
    // 结构: C, F, B
    public static final int FEELING_SPAN_LENGTH = 1920 * 1080 * 24;
    public static final int BEHAVIOR_SPAN_LENGTH = 256;
    public static final int INHERITANCE_INFO_SPAN_LENGTH = FEELING_SPAN_LENGTH * 3;

    public static final int TOTAL_LENGTH = FEELING_SPAN_LENGTH +
            BEHAVIOR_SPAN_LENGTH +
            INHERITANCE_INFO_SPAN_LENGTH;

    private final FeelingSpan feelingSpan;
    private final BehaviorSpan behaviorSpan;
    private final InheritanceInfoSpan inheritanceInfoSpan;

    public OutputVectorDomain() {
        int currentOffset = 0;

        // C: 系统状态层（最稳定）
        this.inheritanceInfoSpan = new InheritanceInfoSpan(currentOffset, INHERITANCE_INFO_SPAN_LENGTH);
        currentOffset += INHERITANCE_INFO_SPAN_LENGTH;

        // F: 感知反馈层（实时更新）
        this.feelingSpan = new FeelingSpan(currentOffset, FEELING_SPAN_LENGTH);
        currentOffset += FEELING_SPAN_LENGTH;

        // B: 行为输出层（微小数据）
        this.behaviorSpan = new BehaviorSpan(currentOffset, BEHAVIOR_SPAN_LENGTH);
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