package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.SlidingPair;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import java.io.Closeable;
import java.io.IOException;

/**
 * urana 系统中锚点的抽象基类。
 * 定义了 urana 锚点的通用结构：包含一个“感觉”和一个“行为”的滑动对。
 * 并提供了通用的 tick() 和 close() 实现。
 * <p>
 * 设计原则（真善美第2条）：锚点是 urana 意识体的"记忆"模式，其向量是 urana 状态，
 * 类型为 {@link VectorBase}（非 BoolVector），由 {@link INeuralNetwork#createVector} 创建——
 * urana 状态不绑 BNN 载体，换 NN 时 Anchor 零改动。
 */
public abstract class AbstractAnchor implements Closeable {

    protected final SlidingPair<VectorBase> feeling;
    protected final SlidingPair<VectorBase> behavior;

    /**
     * @param nn           神经网络抽象边界，用于创建状态向量（载体类型由 nn 决定）。
     * @param feelingSize  感觉向量尺寸。
     * @param behaviorSize 行为向量尺寸。
     */
    public AbstractAnchor(INeuralNetwork nn, int feelingSize, int behaviorSize) {
        this.feeling = new SlidingPair<>(nn.createVector(feelingSize), nn.createVector(feelingSize));
        this.behavior = new SlidingPair<>(nn.createVector(behaviorSize), nn.createVector(behaviorSize));
    }

    public SlidingPair<VectorBase> getFeeling() {
        return feeling;
    }

    public SlidingPair<VectorBase> getBehavior() {
        return behavior;
    }

    /**
     * 将感觉和行为窗口同步向前滑动一个时间步。
     * 子类可以根据需要覆盖此方法以实现不同的滑动逻辑，但 urana 系统中默认为同步滑动。
     */
    public void tick() {
        feeling.slide();
        behavior.slide();
    }

    // === 路径一：区间覆盖（Prospective / Retrospective 的通用回退）===
    public void pushFeeling(VectorBase source, Span srcSpan, Span destSpan, long streamHandle) {
        feeling.push(source, srcSpan, destSpan, streamHandle);
    }

    public void pushBehavior(VectorBase source, Span srcSpan, Span destSpan, long streamHandle) {
        behavior.push(source, srcSpan, destSpan, streamHandle);
    }

    // === 路径二：零拷贝交换（仅当上游已产出完整独立数组时）===
    public void pushFeeling(VectorBase source) {
        feeling.push(source); // 所有权转移
    }

    public void pushBehavior(VectorBase source) {
        behavior.push(source); // 所有权转移
    }

    /**
     * 将源向量的数据拷贝到 feeling 的悬浮物中，不转移所有权。
     */
    public void pushFeelingFrom(VectorBase source, long streamHandle) {
        feeling.getSuspension().copyRegionFrom(source,
            new Span(0, source.size()) {},
            new Span(0, feeling.getSuspension().size()) {},
            streamHandle);
    }

    /**
     * 将源向量的数据拷贝到 behavior 的悬浮物中，不转移所有权。
     */
    public void pushBehaviorFrom(VectorBase source, long streamHandle) {
        behavior.getSuspension().copyRegionFrom(source,
            new Span(0, source.size()) {},
            new Span(0, behavior.getSuspension().size()) {},
            streamHandle);
    }

    @Override
    public void close() throws IOException {
        feeling.close();
        behavior.close();
    }
}
