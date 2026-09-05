package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.bnn_mapper;

import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

class BitplaneEncoderNative {
    static {
        NativeLibLoader.ensureLoaded();
    }

    /**
     * 从快照纹理解码位平面到 BoolVector（CPU侧异步，GPU侧顺序执行）。
     * native 侧无持久资源：Map→kernel→Unmap 每次调用内完成，texObj 即建即毁。
     * 解码区域 = 快照纹理全域（尺寸由 bridge 从纹理自取）。
     *
     * @param snapshotTextureHandle 快照 Texture 对象的 C++ 指针 (Texture*，bridge 内读尺寸与 cudaResource)
     * @param bitOffset            在 BoolVector 中的起始位偏移量（32 位对齐）
     * @param streamHandle         CUDA 流句柄
     * @param dstVectorHandle      目标 BoolVector 的设备指针句柄
     */
    static native void _encode(
            long snapshotTextureHandle,
            long bitOffset,
            long streamHandle,
            /*->*/ long dstVectorHandle);
}