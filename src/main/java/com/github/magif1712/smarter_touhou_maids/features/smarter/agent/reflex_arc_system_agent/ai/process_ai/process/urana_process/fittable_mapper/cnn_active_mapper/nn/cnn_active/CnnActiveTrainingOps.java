package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper.nn.cnn_active;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.cnn.containers.CnnFwTraceForBw;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.cnn.containers.CnnHyperparameters;

/**
 * CNN-Active 训练桥接层（照抄 {@code CnnTrainingOps} + 两个新入参）：接收上层对象，提取句柄，
 * 调用原生方法。
 * <p>
 * <b>trace_z 在本模块被真正使用</b>：clipped-linear 的导数 {@code (|z|<1) ? 1 : 0} 需要 pre-activation
 * 值。原 CNN 用 {@code y(1−y)}（sigmoid 导数）可直接由 y 推出，故 {@code trace_z} 是 {@code (void)} 掉的；
 * 本模块改激活后 z 成为必需——这是 D2 的直接后果，不是额外设计。
 * <p>
 * {@code bufHp} 始终更新权重+刷新 cache（从 {@code bufHp} 提取 p/q/l/r/b/idx/w 句柄）。
 * {@code bufTc} 非 {@code null} 时外拷输入层梯度；{@code null} 时跳过（传 0）。
 * <p>
 * 跨 NN 权重更新要求结构一致：读侧 {@code hp} 算梯度、写侧 {@code bufHp} 落权重，结构不同会越界——
 * 故校验 {@code sizeA0/sizeA1} 一致。{@code lr} 由调用方（{@link CnnActiveNeuralNetwork}）提供，
 * 作为训练配置不污染权重容器。
 */
public class CnnActiveTrainingOps {

    public static void cnnActiveBackwardLayer(CnnFwTraceForBw trace, FloatVector y, CnnHyperparameters hp, FloatVector target, FloatVector prevB, int activationId, FloatVector x, float lr, long stream /* -> */, FloatVector dz, FloatVector dInput, FloatVector bufTc, CnnHyperparameters bufHp) {
        if (trace == null) {
            throw new IllegalArgumentException("cnnActiveBackwardLayer 需要 trace（clipped-linear 的导数依赖 trace_z）。");
        }
        if (bufHp.getSizeA0() != hp.getSizeA0() || bufHp.getSizeA1() != hp.getSizeA1()) {
            throw new IllegalArgumentException("buf_hp 与 hp 结构不一致，无法跨 NN 更新权重。");
        }

        long bufTcHandle = (bufTc != null) ? bufTc.requireHandle() : 0L;
        int sizeC = (bufTc != null) ? bufTc.size() : 0;
        long buf_p = bufHp.getP().requireHandle();
        long buf_q = bufHp.getQ().requireHandle();
        long buf_l = bufHp.getL().requireHandle();
        long buf_r = bufHp.getR().requireHandle();
        long buf_b = bufHp.getB().requireHandle();
        long buf_idx0 = bufHp.getIdx0().requireHandle();
        long buf_idx1 = bufHp.getIdx1().requireHandle();
        long buf_w0 = bufHp.getW0().requireHandle();
        long buf_w1 = bufHp.getW1().requireHandle();

        CnnActiveTrainingOpsNative._cnnActiveBackwardLayer(trace.z.requireHandle(), y.requireHandle(), target.requireHandle(), x.requireHandle(), prevB.requireHandle(), activationId, hp.getP().requireHandle(), hp.getQ().requireHandle(), hp.getL().requireHandle(), hp.getR().requireHandle(), hp.getB().requireHandle(), hp.getIdx0().requireHandle(), hp.getIdx1().requireHandle(), hp.getW0().requireHandle(), hp.getW1().requireHandle(), hp.getSizeA0(), hp.getSizeA1(), sizeC, lr, stream /* -> */, buf_p, buf_q, buf_l, buf_r, buf_b, buf_idx0, buf_idx1, buf_w0, buf_w1, dz.requireHandle(), dInput.requireHandle(), bufTcHandle);
    }
}
