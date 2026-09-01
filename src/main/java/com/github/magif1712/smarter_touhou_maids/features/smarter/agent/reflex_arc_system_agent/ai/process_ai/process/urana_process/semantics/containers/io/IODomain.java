package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.NnEncodingProfile;

/**
 * IO 域：把"输入域 + 输出域"这个不实在的对偶，实在化为一个对象（真善美第4条）。
 * <p>
 * 由 UranaProcessFactory 据 profile 创建，注入 UranaSystem。urana 持域实例而非静态 OUTPUT_DOMAIN，
 * 使多实例隔离与换 nn 时的布局切换成为可能（真善美第3条）。
 */
public class IODomain {
    private final InputVectorDomain inputDomain;
    private final OutputVectorDomain outputDomain;

    public IODomain(NnEncodingProfile profile) {
        this.inputDomain = new InputVectorDomain(profile);
        this.outputDomain = new OutputVectorDomain(profile);
    }

    public InputVectorDomain getInputDomain() {
        return inputDomain;
    }

    public OutputVectorDomain getOutputDomain() {
        return outputDomain;
    }
}
