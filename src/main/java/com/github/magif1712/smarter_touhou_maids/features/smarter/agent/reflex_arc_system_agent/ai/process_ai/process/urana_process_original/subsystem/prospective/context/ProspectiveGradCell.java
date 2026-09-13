package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.subsystem.prospective.context;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.common.UranaConstants;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.common.grad.AbstractGradCell;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.subsystem.prospective.ProspectiveAnchor;

/**
 * 流程三：拟合过去1刻（两阶段训练）。
 * 为 ProspectiveAnchor 服务，用过去的实际数据来校准自己。
 * 继承 AbstractGradCell，单步链条，内部闭环跨轮次梯度。
 */
public class ProspectiveGradCell extends AbstractGradCell {
    private static final int CHAIN_LENGTH = 1;

    public ProspectiveGradCell(INeuralNetwork nn) {
        super(nn, CHAIN_LENGTH, UranaConstants.G_PAST_1);
    }

    /**
     * 全自动执行。外界不需要知道 ∇C 的存在。
     *
     * @param dtMillis 训练时间间隔（prospective 用 fastDt，与推理一致）。
     */
    public void execute(ProspectiveAnchor anchor, long dtMillis, long stream) {
        // 填充预分配池
        samplePool_original[0].set(
            anchor.getFeeling().getPrecipitate(),
            anchor.getBehavior().getPrecipitate()
        );
        super.executeWithClosedLoop(
            anchor.getFeeling().getSuspension(), dtMillis, stream);
    }
}
