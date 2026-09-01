package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.subsystem.introspective.context;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.common.UranaConstants;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.common.grad.AbstractGradCell;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.subsystem.introspective.IntrospectiveAnchor;

/**
 * 流程八：内省训练上下文。
 * 继承 AbstractGradCell，两步链条（展望→审视），内部闭环跨轮次梯度。
 */
public class TrainingContext extends AbstractGradCell {
    private static final boolean[][] G_PER_STEP = {
        UranaConstants.G_FUTURE_N,
        UranaConstants.G_PAST_N
    };
    private static final int CHAIN_LENGTH = 2;

    public TrainingContext(INeuralNetwork nn) {
        super(nn, CHAIN_LENGTH, G_PER_STEP);
    }

    /**
     * 全自动执行。外界不需要知道 ∇C 的存在。
     *
     * @param dtMillis 训练时间间隔（introspective 用 slowDt）。
     */
    public void execute(IntrospectiveAnchor anchor, long dtMillis, long stream) {
        // 填充预分配池
        samplePool_original[0].set(
            anchor.getFeeling().getSuspension(),
            anchor.getBehavior().getSuspension()
        );
        samplePool_original[1].set(
            anchor.getFeeling().getPrecipitate(),
            anchor.getBehavior().getPrecipitate()
        );

        super.executeWithClosedLoop(
            anchor.getFeeling().getPrecipitate(), dtMillis, stream);
    }
}
