package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.subsystem.prospective;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.common.AbstractAnchor;

/**
 * 流程一（感觉-行为滑动窗口）的上下文 data 容器。
 * 代表一个向前看的、探索性的锚点。（向未来滑动）
 */
public class ProspectiveAnchor extends AbstractAnchor {

    public ProspectiveAnchor(INeuralNetwork nn, int feelingSize, int behaviorSize) {
        super(nn, feelingSize, behaviorSize);
    }

    // 所有 tick(), close(), getFeeling(), getBehavior() 的实现都已继承自 AbstractAnchor！
    // 这里可以根据需要添加 ProspectiveAnchor 特有的方法。
}
