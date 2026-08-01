package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.subsystem.retrospective.context;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common.UranaConstants;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common.inference.AbstractInferenceCell;

/**
 * 流程四：分析师推理。
 * N=1 单步，G=PAST_1。每 tick 执行一步，F 和 C 由 UranaSystem 跨 tick 维护。
 */
public class RetrospectiveInference extends AbstractInferenceCell {
    private static final int CHAIN_LENGTH = 1;

    public RetrospectiveInference(INeuralNetwork nn) {
        super(nn, CHAIN_LENGTH, UranaConstants.G_PAST_1);
    }
}