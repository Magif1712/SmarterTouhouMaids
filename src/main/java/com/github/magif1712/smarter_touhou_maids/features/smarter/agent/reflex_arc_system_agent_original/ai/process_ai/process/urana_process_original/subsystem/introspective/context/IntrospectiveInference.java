package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.subsystem.introspective.context;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.common.UranaConstants;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.common.inference.AbstractInferenceCell;

/**
 * 流程七：内省者推理。
 * N=1 单步，G=FUTURE_1。
 */
public class IntrospectiveInference extends AbstractInferenceCell {
    private static final int CHAIN_LENGTH = 1;

    public IntrospectiveInference(INeuralNetwork nn) {
        super(nn, CHAIN_LENGTH, UranaConstants.G_FUTURE_1);
    }
}