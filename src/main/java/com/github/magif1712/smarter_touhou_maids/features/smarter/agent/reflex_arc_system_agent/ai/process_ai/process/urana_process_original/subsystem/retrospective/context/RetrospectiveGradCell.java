package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.subsystem.retrospective.context;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.common.UranaConstants;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.common.grad.AbstractGradCell;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.subsystem.retrospective.RetrospectiveAnchor;

/**
 * 流程六：拟合未来1刻（两阶段训练）。
 * 为 RetrospectiveAnchor 服务，用未来的实际数据来校准自己。
 * 继承 AbstractGradCell，单步链条，内部闭环跨轮次梯度。
 */
public class RetrospectiveGradCell extends AbstractGradCell {
    private static final int CHAIN_LENGTH = 1;

    public RetrospectiveGradCell(INeuralNetwork nn) {
        super(nn, CHAIN_LENGTH, UranaConstants.G_FUTURE_1);
    }

    /**
     * 全自动执行。外界不需要知道 ∇C 的存在。
     *
     * @param dtMillis 训练时间间隔（retrospective 用 slowDt）。
     */
    public void execute(RetrospectiveAnchor anchor, long dtMillis, long stream) {
        // 填充预分配池：目标是"现在"（悬浮物），输入是"过去"（沉淀物）
        samplePool_original[0].set(
                anchor.getFeeling().getSuspension(),
                anchor.getBehavior().getSuspension()
        );
        super.executeWithClosedLoop(
                anchor.getFeeling().getPrecipitate(), dtMillis, stream);
    }
}
