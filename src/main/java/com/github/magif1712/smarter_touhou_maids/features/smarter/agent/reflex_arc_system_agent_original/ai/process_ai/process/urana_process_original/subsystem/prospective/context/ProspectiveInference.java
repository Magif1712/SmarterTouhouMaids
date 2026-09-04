package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.subsystem.prospective.context;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.common.UranaConstants;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.common.inference.AbstractInferenceCell;

/**
 * 流程二：行动者推理。
 * N=2 链条：阶段1 展望未来(G_FUTURE_N) → 阶段2 审视过去(G_PAST_N)。
 */
public class ProspectiveInference extends AbstractInferenceCell {
    private static final boolean[][] G_PER_STEP = {
        UranaConstants.G_FUTURE_N,
        UranaConstants.G_PAST_N
    };
    private static final int CHAIN_LENGTH = 2;

    public ProspectiveInference(INeuralNetwork nn) {
        super(nn, CHAIN_LENGTH, G_PER_STEP);
    }
}