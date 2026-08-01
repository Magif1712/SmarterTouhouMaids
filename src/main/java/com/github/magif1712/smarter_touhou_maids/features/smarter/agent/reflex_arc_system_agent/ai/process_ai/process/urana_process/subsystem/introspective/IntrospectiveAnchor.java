package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.subsystem.introspective;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.SlidingPair;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common.AbstractAnchor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.OutputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.BehaviorSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.FeelingSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.subsystem.retrospective.RetrospectiveAnchor;

/**
 * 流程七（内省分析）的上下文 data 容器。
 * 代表一个审视现在、规划未来的锚点。
 */
public class IntrospectiveAnchor extends AbstractAnchor {

    public IntrospectiveAnchor(INeuralNetwork nn, int feelingSize, int behaviorSize) {
        super(nn, feelingSize, behaviorSize);
    }

    /**
     * 从 RetrospectiveAnchor 的“悬浮物”中“戳入”数据，固化为本锚点的“沉淀物”。
     */
    public void pokeFrom(RetrospectiveAnchor retrospectiveAnchor, long streamHandle) {
        // “戳入”操作：将 retrospectiveAnchor 的悬浮物（current）固化为本锚点的沉淀物（previous）
        // 使用复制语义，因为 retrospectiveAnchor 的状态在后续可能仍需使用，不能移动。

        // 1. 复制 feeling
        VectorBase feelingSource = retrospectiveAnchor.getFeeling().getSuspension();
        FeelingSpan feelingSpan = new FeelingSpan(0, feelingSource.size());
        this.feeling.copyRegionTo(SlidingPair.Target.PRECIPITATE, feelingSource, feelingSpan, feelingSpan, streamHandle);

        // 2. 复制 behavior
        VectorBase behaviorSource = retrospectiveAnchor.getBehavior().getSuspension();
        BehaviorSpan behaviorSpan = new BehaviorSpan(0, behaviorSource.size());
        this.behavior.copyRegionTo(SlidingPair.Target.PRECIPITATE, behaviorSource, behaviorSpan, behaviorSpan, streamHandle);
    }

    /**
     * 将推理结果“戳入”本锚点的“悬浮物”中。
     *
     * @param feeling       推理产生的输出向量（urana 适配器从 nn 拷出的 VectorBase，含 feeling/behavior 区段）。
     * @param behavior      与 feeling 同一输出向量（语义上分两次提取不同区段）。
     * @param streamHandle  CUDA 流句柄。
     */
    public void pokeInto(VectorBase feeling, VectorBase behavior, long streamHandle) {
        // feeling 和 behavior 参数实际上是同一个完整的“输出向量”。
        // 我们需要从中提取出 feeling 和 behavior 各自对应的部分。

        OutputVectorDomain sourceDomain = new OutputVectorDomain();

        // 处理 feeling 部分
        FeelingSpan feelingSrcSpan = sourceDomain.getFeelingSpan();
        FeelingSpan feelingDestSpan = new FeelingSpan(0, this.feeling.getSuspension().size());
        this.feeling.push(feeling, feelingSrcSpan, feelingDestSpan, streamHandle);

        // 处理 behavior 部分
        BehaviorSpan behaviorSrcSpan = sourceDomain.getBehaviorSpan();
        BehaviorSpan behaviorDestSpan = new BehaviorSpan(0, this.behavior.getSuspension().size());
        this.behavior.push(behavior, behaviorSrcSpan, behaviorDestSpan, streamHandle);
    }
}
