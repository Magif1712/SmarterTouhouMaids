package com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.io.subspan;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;

/**
 * InheritanceInfo 区间：输入/输出向量中继承信息 C 分量的定位。
 * <p>
 * 仅是 {@link Span} 的具名子类，不增加任何行为（真善美第4条：语义即类型）。
 */
public class InheritanceInfoSpan extends Span {
    public InheritanceInfoSpan(int offset, int length) {
        super(offset, length);
    }
}
