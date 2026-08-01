package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.subsystem.introspective.context;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common.UranaConstants;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common.inference.AbstractInferenceCell;

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