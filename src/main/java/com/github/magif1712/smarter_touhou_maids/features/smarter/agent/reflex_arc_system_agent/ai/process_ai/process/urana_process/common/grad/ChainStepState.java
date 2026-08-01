package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common.grad;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;

/**
 * 封装了链式处理中单步前向传播产生的、反向传播所必需的内部状态。
 * <p>
 * 字段类型为 {@link VectorBase}（非 BoolVector），由调用方（AbstractGradCell）通过
 * nn.createVector 创建——载体类型由 nn 决定，urana 不绑 BNN 的 BoolVector。
 */
public class ChainStepState implements AutoCloseable {
    public final VectorBase input;
    public final VectorBase fz; // 隐藏层预激活值
    public final VectorBase output;

    public ChainStepState(VectorBase input, VectorBase fz, VectorBase output) {
        this.input = input;
        this.fz = fz;
        this.output = output;
    }

    @Override
    public void close() throws Exception {
        if (input != null) input.close();
        if (fz != null) fz.close();
        if (output != null) output.close();
    }
}
