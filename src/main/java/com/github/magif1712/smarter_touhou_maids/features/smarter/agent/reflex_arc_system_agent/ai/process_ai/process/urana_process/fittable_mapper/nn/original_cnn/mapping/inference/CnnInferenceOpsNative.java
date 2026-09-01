package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.mapping.inference;

import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

/**
 * CNN 推理原生方法层（对应 BnnInferenceOpsNative）：声明 CUDA kernel 入口。
 * <p>
 * 仅接收句柄与标量，不接触 Java 对象。{@code traceZ/traceY} 为 0 时走 NoTrace 路径
 * （z 用 y 做工作区：push→pull→activate 覆盖 y）；非 0 时走 StoreTrace 路径
 * （z=traceZ 累加，y=traceY 写 σ(z)）。C 侧模板 {@code <bool StoreTrace>} 编译期优化，
 * JNI 层据 {@code traceZ==0} 选 bridge。
 * <p>
 * {@code _cnnRefreshCache} 由 p 重算 idx/w（非热路径，构造/loadFromFile 后一次性调用）。
 */
class CnnInferenceOpsNative {
    static {
        NativeLibLoader.ensureLoaded();
    }

    static native void _cnnForwardLayer(long x, long p, long q, long l, long r, long b, long idx0, long idx1, long w0, long w1, int sizeA0, int sizeA1, long stream /* -> */, long y, long traceZ, long traceY);

    static native void _cnnRefreshCache(long p, int sizeA0, int sizeA1, long stream /* -> */, long idx0, long idx1, long w0, long w1);
}
