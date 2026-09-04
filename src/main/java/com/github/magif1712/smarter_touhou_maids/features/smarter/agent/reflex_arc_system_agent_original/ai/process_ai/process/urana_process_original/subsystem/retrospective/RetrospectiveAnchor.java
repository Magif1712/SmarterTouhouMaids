package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.subsystem.retrospective;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.common.AbstractAnchor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.semantics.containers.io.OutputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.semantics.containers.io.subspan.BehaviorSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.semantics.containers.io.subspan.FeelingSpan;

/**
 * 流程五（回溯分析）的上下文 data 容器。
 * 代表一个向后看的、分析性的锚点。（向过去滑动）
 * <p>
 * 持注入的 {@link OutputVectorDomain} 供 {@link #pushFromOutput} 按语义区间提取 feeling/behavior。
 * domain 由 UranaSystem 构造时注入（profile 已下沉到 nn，domain 只持 urana 的布局）。
 */
public class RetrospectiveAnchor extends AbstractAnchor {

    private final OutputVectorDomain outputDomain_original;

    public RetrospectiveAnchor(INeuralNetwork nn, int feelingSize, int behaviorSize, OutputVectorDomain outputDomain) {
        super(nn, feelingSize, behaviorSize);
        this.outputDomain_original = outputDomain;
    }

    /**
     * 从一个输出向量（urana 适配器从 nn 拷出来的 VectorBase）中按语义区间提取 feeling 和 behavior。
     */
    public void pushFromOutput(VectorBase source, long streamHandle) {
        pushFeeling(source, outputDomain_original.getFeelingSpan(),
                new FeelingSpan(0, outputDomain_original.getFeelingSpan().getLength()), streamHandle);
        pushBehavior(source, outputDomain_original.getBehaviorSpan(),
                new BehaviorSpan(0, outputDomain_original.getBehaviorSpan().getLength()), streamHandle);
    }
}
