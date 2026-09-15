package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper.nn.cnn_active;

import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

/**
 * CNN-Active 训练原生方法层（对应 {@code CnnTrainingOpsNative}）：声明 CUDA kernel 入口。
 * <p>
 * 单一 {@code _cnnActiveBackwardLayer} 方法承载两阶段反向（同一 host 函数内 launch 两个 kernel）：
 * <ul>
 *   <li>Kernel1（sizeA1）：δ_j = 2(y−y′)·a′(z) → dz[j]；∂l/∂r = δ·邻域真值（B 段取 prevB）；
 *       ∂b = δ；buf_l/r/b -= lr·grad</li>
 *   <li>Kernel2（sizeA0）：∂x_i = Σδ[idx]·q·w + δ[i+1]·l[i+1]·[i+1&lt;bOffset]
 *       + δ[i-1]·r[i-1]·[i-1&lt;bOffset] → dInput+bufTc；
 *       ∂p/∂q from hp's p/q/idx/w；buf_p/q -= lr·grad；clamp buf_p[0,m-1]；refresh buf_idx/w</li>
 * </ul>
 * <b>与原 CNN 的差异</b>：多 {@code prevB}（D4）与 {@code activationId}（D2）两个入参；
 * 且 native 侧**启用 {@code trace_z}**（clipped-linear 的导数需 {@code |z|<1} 判定，
 * 原实现显式 {@code (void)trace_z}）。
 * <p>
 * hp=读侧（前向权重），bufHp=写侧。l/r 输入梯度项读 hp（旧值，若 bufHp==hp 则已被 Kernel1
 * 更新→可接受噪声，与 BNN/原 CNN 同理）。
 */
class CnnActiveTrainingOpsNative {
    static {
        NativeLibLoader.ensureLoaded();
    }

    static native void _cnnActiveBackwardLayer(long traceZ, long traceY, long target, long x, long prevB, int activationId, long hp_p, long hp_q, long hp_l, long hp_r, long hp_b, long hp_idx0, long hp_idx1, long hp_w0, long hp_w1, int sizeA0, int sizeA1, int sizeC, float lr, long stream /* -> */, long buf_p, long buf_q, long buf_l, long buf_r, long buf_b, long buf_idx0, long buf_idx1, long buf_w0, long buf_w1, long dz, long dInput, long bufTc);
}
