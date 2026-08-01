package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Domain;
import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;

/**
 * IO 语义域的聚合器。
 * <p>
 * 这个类聚合了 InputVectorSpan 和 OutputVectorSpan，
 * 为上层应用提供了一个统一的、描述IO语义布局的访问入口。
 * 它是一个纯粹的、无状态的描述符集合。
 */
public class IODomain extends Domain<Span> {
    private final InputVectorDomain inputDomain;
    private final OutputVectorDomain outputDomain;

    public IODomain() {
        this.inputDomain = new InputVectorDomain();
        this.outputDomain = new OutputVectorDomain();
    }

    public InputVectorDomain getInputDomain() {
        return inputDomain;
    }

    public OutputVectorDomain getOutputDomain() {
        return outputDomain;
    }

    @Override
    public boolean contains(Span element) {
        return inputDomain.contains(element) || outputDomain.contains(element);
    }
}