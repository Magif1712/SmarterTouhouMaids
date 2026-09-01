package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;

/**
 * Feeling 区间：输入/输出向量中 feeling 分量的定位。
 * <p>
 * 仅是 {@link Span} 的具名子类（把"这段是 feeling"这个不实在语义实在化为类型），
 * 不增加任何行为（真善美第4条：语义即类型）。
 */
public class FeelingSpan extends Span {
    public FeelingSpan(int offset, int length) {
        super(offset, length);
    }
}
