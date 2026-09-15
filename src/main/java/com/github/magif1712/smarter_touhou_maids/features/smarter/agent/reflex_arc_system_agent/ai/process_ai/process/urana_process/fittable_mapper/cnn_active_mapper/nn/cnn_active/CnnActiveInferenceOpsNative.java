package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper.nn.cnn_active;

import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

/**
 * CNN-Active 推理原生方法层（对应 {@code CnnInferenceOpsNative}）：声明 CUDA kernel 入口。
 * <p>
 * 仅接收句柄与标量，不接触 Java 对象。{@code traceZ} 为 0 时走 NoTrace 路径，非 0 时走 StoreTrace 路径。
 * {@code prevB} 是本 NN 自持的"上一拍行为段"（256 float），{@code activationId} 是 D2 的显式入参。
 * <p>
 * {@code _cnnActiveRefreshCache} 由 p 重算 idx/w（非热路径，构造/loadFromFile 后一次性调用）。
 * 刷新逻辑随本模块独立演化（不与原 CNN 共享 idx/w 的"生产者"，方案 §六 划分规则）。
 */
class CnnActiveInferenceOpsNative {
    static {
        NativeLibLoader.ensureLoaded();
    }

    static native void _cnnActiveForwardLayer(long x, long p, long q, long l, long r, long b, long idx0, long idx1, long w0, long w1, long prevB, int activationId, int sizeA0, int sizeA1, long stream /* -> */, long traceZ, long traceY);

    static native void _cnnActiveRefreshCache(long p, int sizeA0, int sizeA1, long stream /* -> */, long idx0, long idx1, long w0, long w1);
}
