package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.subsystem.introspective;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.SlidingPair;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.common.AbstractAnchor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.semantics.containers.io.OutputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.semantics.containers.io.subspan.BehaviorSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.semantics.containers.io.subspan.FeelingSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.subsystem.retrospective.RetrospectiveAnchor;

/**
 * 流程七（内省分析）的上下文 data 容器。
 * 代表一个审视现在、规划未来的锚点。
 * <p>
 * 持注入的 {@link OutputVectorDomain} 供 {@link #pokeInto} 按语义区间提取 feeling/behavior。
 * domain 由 UranaSystem 构造时注入（profile 已下沉到 nn，domain 只持 urana 的布局）。
 */
public class IntrospectiveAnchor extends AbstractAnchor {

    private final OutputVectorDomain outputDomain_original;

    public IntrospectiveAnchor(INeuralNetwork nn, int feelingSize, int behaviorSize, OutputVectorDomain outputDomain) {
        super(nn, feelingSize, behaviorSize);
        this.outputDomain_original = outputDomain;
    }

    /**
     * 从 RetrospectiveAnchor 的"悬浮物"中"戳入"数据，固化为本锚点的"沉淀物"。
     */
    public void pokeFrom(RetrospectiveAnchor retrospectiveAnchor, long streamHandle) {
        // "戳入"操作：将 retrospectiveAnchor 的悬浮物（current）固化为本锚点的沉淀物（previous）
        // 使用复制语义，因为 retrospectiveAnchor 的状态在后续可能仍需使用，不能移动。

        // 1. 复制 feeling
        VectorBase feelingSource = retrospectiveAnchor.getFeeling().getSuspension();
        FeelingSpan feelingSpan = new FeelingSpan(0, feelingSource.size());
        this.feeling_original.copyRegionTo(SlidingPair.Target.PRECIPITATE, feelingSource, feelingSpan, feelingSpan, streamHandle);

        // 2. 复制 behavior
        VectorBase behaviorSource = retrospectiveAnchor.getBehavior().getSuspension();
        BehaviorSpan behaviorSpan = new BehaviorSpan(0, behaviorSource.size());
        this.behavior_original.copyRegionTo(SlidingPair.Target.PRECIPITATE, behaviorSource, behaviorSpan, behaviorSpan, streamHandle);
    }

    /**
     * 将推理结果"戳入"本锚点的"悬浮物"中。
     *
     * @param feeling       推理产生的输出向量（urana 适配器从 nn 拷出的 VectorBase，含 feeling/behavior 区段）。
     * @param behavior      与 feeling 同一输出向量（语义上分两次提取不同区段）。
     * @param streamHandle  CUDA 流句柄。
     */
    public void pokeInto(VectorBase feeling, VectorBase behavior, long streamHandle) {
        // feeling 和 behavior 参数实际上是同一个完整的"输出向量"。
        // 从中提取出 feeling 和 behavior 各自对应的部分（用注入的 outputDomain_original，不再临时 new）。

        // 处理 feeling 部分
        FeelingSpan feelingSrcSpan = outputDomain_original.getFeelingSpan();
        FeelingSpan feelingDestSpan = new FeelingSpan(0, this.feeling_original.getSuspension().size());
        this.feeling_original.push(feeling, feelingSrcSpan, feelingDestSpan, streamHandle);

        // 处理 behavior 部分
        BehaviorSpan behaviorSrcSpan = outputDomain_original.getBehaviorSpan();
        BehaviorSpan behaviorDestSpan = new BehaviorSpan(0, this.behavior_original.getSuspension().size());
        this.behavior_original.push(behavior, behaviorSrcSpan, behaviorDestSpan, streamHandle);
    }
}
