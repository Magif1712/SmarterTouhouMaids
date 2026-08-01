package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.subsystem.retrospective;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common.AbstractAnchor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.OutputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.BehaviorSpan;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.FeelingSpan;

/**
 * 流程五（回溯分析）的上下文 data 容器。
 * 代表一个向后看的、分析性的锚点。（向过去滑动）
 */
public class RetrospectiveAnchor extends AbstractAnchor {

    private static final OutputVectorDomain OUTPUT_DOMAIN = new OutputVectorDomain();

    public RetrospectiveAnchor(INeuralNetwork nn, int feelingSize, int behaviorSize) {
        super(nn, feelingSize, behaviorSize);
    }

    /**
     * 从一个输出向量（urana 适配器从 nn 拷出来的 VectorBase）中按语义区间提取 feeling 和 behavior。
     */
    public void pushFromOutput(VectorBase source, long streamHandle) {
        pushFeeling(source, OUTPUT_DOMAIN.getFeelingSpan(),
                new FeelingSpan(0, OUTPUT_DOMAIN.getFeelingSpan().getLength()), streamHandle);
        pushBehavior(source, OUTPUT_DOMAIN.getBehaviorSpan(),
                new BehaviorSpan(0, OUTPUT_DOMAIN.getBehaviorSpan().getLength()), streamHandle);
    }
}
