package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.mapping.inference;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.containers.CnnFwTraceForBw;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.containers.CnnHyperparameters;

/**
 * CNN 推理桥接层（对应 BnnInferenceOps）：接收上层对象，提取句柄，调用原生方法。
 * <p>
 * {@code trace} 为 {@code null} 时纯推理（NoTrace：z 用 y 做工作区，push→pull→activate 覆盖 y）；
 * 非 {@code null} 时训练前向（StoreTrace：z=trace.z 累加，y=trace.y 写 σ(z)）。
 * <p>
 * {@code cnnRefreshCache} 由 {@code p} 重算 {@code idx0/idx1/w0/w1}（非热路径，stream 0 + 同步）。
 */
public class CnnInferenceOps {

    public static void cnnForwardLayer(FloatVector x, CnnHyperparameters hp, long stream /* -> */, FloatVector y, CnnFwTraceForBw trace) {
        long traceZ = (trace != null) ? trace.z.requireHandle() : 0L;
        long traceY = (trace != null) ? trace.y.requireHandle() : 0L;
        CnnInferenceOpsNative._cnnForwardLayer(x.requireHandle(), hp.getP().requireHandle(), hp.getQ().requireHandle(), hp.getL().requireHandle(), hp.getR().requireHandle(), hp.getB().requireHandle(), hp.getIdx0().requireHandle(), hp.getIdx1().requireHandle(), hp.getW0().requireHandle(), hp.getW1().requireHandle(), hp.getSizeA0(), hp.getSizeA1(), stream /* -> */, y.requireHandle(), traceZ, traceY);
    }

    public static void cnnRefreshCache(CnnHyperparameters hp, long stream /* -> */) {
        CnnInferenceOpsNative._cnnRefreshCache(hp.getP().requireHandle(), hp.getSizeA0(), hp.getSizeA1(), stream /* -> */, hp.getIdx0().requireHandle(), hp.getIdx1().requireHandle(), hp.getW0().requireHandle(), hp.getW1().requireHandle());
    }
}
