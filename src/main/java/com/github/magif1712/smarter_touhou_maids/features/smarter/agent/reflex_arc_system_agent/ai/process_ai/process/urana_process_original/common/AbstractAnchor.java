package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.common;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.SlidingPair;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.INeuralNetwork;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger("AbstractAnchor");

    /**
     * 神经网络抽象边界引用。
     * <p>
     * 设计原则（真善美第3条）：load 需借 nn.loadVector 造临时实例再 D2D 拷入对应槽——
     * VectorBase 能多态 save，但 load 不知子类，借 nn 对称接口。对称 {@code AbstractGradCell.nn_original}。
     */
    protected final INeuralNetwork nn_original;

    protected final SlidingPair<VectorBase> feeling_original;
    protected final SlidingPair<VectorBase> behavior_original;

    /**
     * @param nn           神经网络抽象边界，用于创建状态向量（载体类型由 nn 决定）。
     * @param feelingSize  感觉向量尺寸。
     * @param behaviorSize 行为向量尺寸。
     */
    public AbstractAnchor(INeuralNetwork nn, int feelingSize, int behaviorSize) {
        this.nn_original = nn;
        this.feeling_original = new SlidingPair<>(nn.createVector(feelingSize), nn.createVector(feelingSize));
        this.behavior_original = new SlidingPair<>(nn.createVector(behaviorSize), nn.createVector(behaviorSize));
    }

    public SlidingPair<VectorBase> getFeeling() {
        return feeling_original;
    }

    public SlidingPair<VectorBase> getBehavior() {
        return behavior_original;
    }

    /**
     * 将感觉和行为窗口同步向前滑动一个时间步。
     * 子类可以根据需要覆盖此方法以实现不同的滑动逻辑，但 urana 系统中默认为同步滑动。
     */
    public void tick() {
        feeling_original.slide();
        behavior_original.slide();
    }

    // === 路径一：区间覆盖（Prospective / Retrospective 的通用回退）===
    public void pushFeeling(VectorBase source, Span srcSpan, Span destSpan, long streamHandle) {
        feeling_original.push(source, srcSpan, destSpan, streamHandle);
    }

    public void pushBehavior(VectorBase source, Span srcSpan, Span destSpan, long streamHandle) {
        behavior_original.push(source, srcSpan, destSpan, streamHandle);
    }

    // === 路径二：零拷贝交换（仅当上游已产出完整独立数组时）===
    public void pushFeeling(VectorBase source) {
        feeling_original.push(source); // 所有权转移
    }

    public void pushBehavior(VectorBase source) {
        behavior_original.push(source); // 所有权转移
    }

    /**
     * 将源向量的数据拷贝到 feeling 的悬浮物中，不转移所有权。
     */
    public void pushFeelingFrom(VectorBase source, long streamHandle) {
        feeling_original.getSuspension().copyRegionFrom(source,
            new Span(0, source.size()) {},
            new Span(0, feeling_original.getSuspension().size()) {},
            streamHandle);
    }

    /**
     * 将源向量的数据拷贝到 behavior 的悬浮物中，不转移所有权。
     */
    public void pushBehaviorFrom(VectorBase source, long streamHandle) {
        behavior_original.getSuspension().copyRegionFrom(source,
            new Span(0, source.size()) {},
            new Span(0, behavior_original.getSuspension().size()) {},
            streamHandle);
    }

    // ==================== 持久化（全窗口 4 槽，与 AbstractGradCell 同构）====================

    /**
     * 全窗口持久化锚点记忆（4 槽：feeling/behavior × precipitate/suspension）。
     * <p>
     * 设计原则（真善美第2条）：意识域 C 中锚点 = 2 槽滑动窗口记忆（两槽都跨轮），
     * 代码域 D 持久化两槽（full window）。文件名带 _precipitate/_suspension
     * 把“存的是哪一槽”这个不实在的语义写进文件名这个实在的东西里（第4条）。
     * <p>
     * 与 {@code AbstractGradCell.save} 同构——锚点是有结构的记忆对象，自管 save/load，
     * 不让 UranaSystem 伸手进 SlidingPair（真善美第3条：UranaSystem 不知锚点内部结构）。
     * 每槽单独 try-catch：一槽失败不阻断其余槽（同 UranaSystem.saveInheritance 形状）。
     *
     * @param uranaPath urana 层持久化目录
     * @param anchorId  锚点标识（prospective/retrospective/introspective）
     */
    public void save(String uranaPath, String anchorId) {
        new File(uranaPath).mkdirs();
        saveSlot(feeling_original.getPrecipitate(), uranaPath, anchorId + "_feeling_precipitate_original.bin");
        saveSlot(feeling_original.getSuspension(), uranaPath, anchorId + "_feeling_suspension_original.bin");
        saveSlot(behavior_original.getPrecipitate(), uranaPath, anchorId + "_behavior_precipitate_original.bin");
        saveSlot(behavior_original.getSuspension(), uranaPath, anchorId + "_behavior_suspension_original.bin");
    }

    private void saveSlot(VectorBase target, String uranaPath, String fileName) {
        if (target == null) return;
        try {
            target.save(new File(uranaPath, fileName).getAbsolutePath());
        } catch (Exception e) {
            LOGGER.warn("[Anchor] save 槽位失败: {}", fileName, e);
        }
    }

    /**
     * 全窗口加载锚点记忆（4 槽）。
     * <p>
     * 文件缺失保持槽位构造期状态（首启动/存档损坏优雅降级）。
     * NULL 流（0L）：load 在 awaken 前，无并发（照搬 UranaSystem.loadInheritance 形状）。
     *
     * @param uranaPath urana 层持久化目录
     * @param anchorId  锚点标识（prospective/retrospective/introspective）
     */
    public void load(String uranaPath, String anchorId) {
        loadSlot(feeling_original.getPrecipitate(), uranaPath, anchorId + "_feeling_precipitate_original.bin");
        loadSlot(feeling_original.getSuspension(), uranaPath, anchorId + "_feeling_suspension_original.bin");
        loadSlot(behavior_original.getPrecipitate(), uranaPath, anchorId + "_behavior_precipitate_original.bin");
        loadSlot(behavior_original.getSuspension(), uranaPath, anchorId + "_behavior_suspension_original.bin");
    }

    private void loadSlot(VectorBase target, String uranaPath, String fileName) {
        File f = new File(uranaPath, fileName);
        if (!f.exists()) return;
        VectorBase loaded = nn_original.loadVector(f.getAbsolutePath());
        try {
            target.copyRegionFrom(loaded, fullSpan(loaded), fullSpan(target), 0L);
        } finally {
            try { loaded.close(); } catch (Exception ignored) {}
        }
    }

    private Span fullSpan(VectorBase vector) {
        return new Span(0, vector.size()) {};
    }

    @Override
    public void close() throws IOException {
        feeling_original.close();
        behavior_original.close();
    }
}
