package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;

/**
 * 锚点时刻数据：一个感觉向量 F + 一个行为向量 B（照搬伪代码 {@code anc.py}）。
 * <p>
 * 字段公开：照搬伪代码 {@code anc.F}/{@code anc.B} 的直接属性访问。
 */
public class Anc {

    public VectorBase F;
    public VectorBase B;

    public Anc(VectorBase F, VectorBase B) {
        this.F = F;
        this.B = B;
    }
}
