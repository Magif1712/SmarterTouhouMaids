package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper.nn.cnn_active;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;

/**
 * CNN-Active 对称初始化桥接层（对应 {@code CnnInferenceOps} 的角色）：接收上层向量对象，
 * 提取句柄，调用原生方法。
 * <p>
 * D1/D3 的 Java 入口（方案 §7.1）：把 {@code q/l/r/b} 原地覆盖为 {@code (−bound, +bound)}
 * 的对称零均值分布，打破原 CNN 的根因病链
 * （{@code fillRandom} 单边非负 ⟹ {@code z ≥ 0} ⟹ 奇激活判决 {@code [z≥0]} 恒真 ⟹ 256 位恒 1 ⟹ 原地不动）。
 * <p>
 * <b>为什么必须对称而不是单边负</b>：单边负 {@code (−B, 0]} ⟹ {@code z} 系统性偏负 ⟹ 落"全 0 吸附点"；
 * 全 0 时拮抗对做差 {@code 0 − 0 = 0} ⟹ 效应器同样不动。单边负只是把病态从一个吸附点平移到另一个。
 */
public class CnnActiveInit {

    /**
     * 原地对称零均值填充。
     *
     * @param v      目标向量（出参：被原地覆盖）
     * @param bound  半径，{@code > 0}（{@code <= 0} 时 native 侧填 0，构造期 fail-fast 由
     *               {@link CnnActiveOptions} 负责）
     * @param seed   64 位种子（不同向量传不同子种子，避免同尺寸向量得到相同随机模式）
     * @param stream CUDA 流句柄（构造期传 0L；native 侧 launch 后同步该流）
     */
    public static void fillSymmetricInPlace(float bound, long seed, long stream /* -> */, FloatVector v) {
        CnnActiveInitNative._fillSymmetric(bound, seed, stream, v.requireHandle());
    }
}
