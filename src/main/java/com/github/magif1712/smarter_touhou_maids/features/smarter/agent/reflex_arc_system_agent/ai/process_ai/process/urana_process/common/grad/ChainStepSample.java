package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common.grad;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;

/**
 * 封装了链式处理中单步所需的所有输入和目标数据，代表一个训练样本。
 * <p>
 * 字段类型为 {@link VectorBase}，与 nn 载体解耦。
 */
public class ChainStepSample {
    public VectorBase target_F;
    public VectorBase target_B;

    public ChainStepSample(VectorBase target_F, VectorBase target_B) {
        set(target_F, target_B);
    }

    public void set(VectorBase target_F, VectorBase target_B) {
        this.target_F = target_F;
        this.target_B = target_B;
    }
}
