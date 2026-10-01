package com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.cnn.mapping.inference;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.cnn.containers.CnnFwTraceForBw;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.cnn.containers.CnnHyperparameters;

/**
 * CNN 推理桥接层（对应 BnnInferenceOps）：接收上层对象，提取句柄，调用原生方法。
 * <p>
 * {@code y} 统一为"kernel 写入的输出缓冲区"——始终为调用方注入的 y（DPS 契约：forward 后 y 即为结果）。
 * native 侧 {@code traceY} 即此 {@code y}，{@code traceZ} 为 0 走 NoTrace，非 0 走 StoreTrace。
 * <p>
 * {@code cnnRefreshCache} 由 {@code p} 重算 {@code idx0/idx1/w0/w1}（非热路径，stream 0 + 同步）。
 */
public class CnnInferenceOps {

    public static void cnnForwardLayer(FloatVector x, CnnHyperparameters hp, long stream /* -> */, FloatVector y, CnnFwTraceForBw trace) {
        long traceZ = (trace != null) ? trace.z.requireHandle() : 0L;
        CnnInferenceOpsNative._cnnForwardLayer(x.requireHandle(), hp.getP().requireHandle(), hp.getQ().requireHandle(), hp.getL().requireHandle(), hp.getR().requireHandle(), hp.getB().requireHandle(), hp.getIdx0().requireHandle(), hp.getIdx1().requireHandle(), hp.getW0().requireHandle(), hp.getW1().requireHandle(), hp.getSizeA0(), hp.getSizeA1(), stream /* -> */, traceZ, y.requireHandle());
    }

    public static void cnnRefreshCache(CnnHyperparameters hp, long stream /* -> */) {
        CnnInferenceOpsNative._cnnRefreshCache(hp.getP().requireHandle(), hp.getSizeA0(), hp.getSizeA1(), stream /* -> */, hp.getIdx0().requireHandle(), hp.getIdx1().requireHandle(), hp.getW0().requireHandle(), hp.getW1().requireHandle());
    }
}