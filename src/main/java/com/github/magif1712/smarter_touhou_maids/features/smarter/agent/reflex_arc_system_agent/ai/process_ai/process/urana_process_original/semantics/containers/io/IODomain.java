package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.semantics.containers.io;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Domain;
import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.NnEncodingProfile;

/**
 * IO 语义域的聚合器。
 * <p>
 * 聚合 {@link InputVectorDomain} 与 {@link OutputVectorDomain}，为上层应用提供统一的 IO 语义布局访问入口。
 * 纯粹的、无状态的描述符集合。
 * <p>
 * 构造收 {@link NnEncodingProfile}，转发给两个子 domain（它们各自用 profile + urana 倍数关系算 span）。
 * profile 由持 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.INeuralNetwork}
 * 引用的组件（AbstractGradCell/AbstractInferenceCell）通过 {@code nn.encodingProfile()} 取得后注入。
 */
public class IODomain extends Domain<Span> {
    private final InputVectorDomain inputDomain_original;
    private final OutputVectorDomain outputDomain_original;

    public IODomain(NnEncodingProfile profile) {
        this.inputDomain_original = new InputVectorDomain(profile);
        this.outputDomain_original = new OutputVectorDomain(profile);
    }

    public InputVectorDomain getInputDomain() {
        return inputDomain_original;
    }

    public OutputVectorDomain getOutputDomain() {
        return outputDomain_original;
    }

    @Override
    public boolean contains(Span element) {
        return inputDomain_original.contains(element) || outputDomain_original.contains(element);
    }
}
