package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper.nn.cnn_active;

import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

/**
 * CNN-Active 对称初始化原生方法层（对应 {@code CnnInferenceOpsNative} 的角色）：
 * 声明 CUDA kernel 入口，仅接收句柄与标量，不接触 Java 对象。
 * <p>
 * {@code _fillSymmetric} 把目标浮点向量原地填为 {@code (−bound, +bound)} 的对称零均值随机值
 * （D1/D3）。native 侧 bridge 在 launch 后同步该 stream——构造期一次性开销，非热路径，
 * 保证 NN 构造返回时权重已写完。
 */
class CnnActiveInitNative {
    static {
        NativeLibLoader.ensureLoaded();
    }

    static native void _fillSymmetric(float bound, long seed, long stream /* -> */, long v);
}
