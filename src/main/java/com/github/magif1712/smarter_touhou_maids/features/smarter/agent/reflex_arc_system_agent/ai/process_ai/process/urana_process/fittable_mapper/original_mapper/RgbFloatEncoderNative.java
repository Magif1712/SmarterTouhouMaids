package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.original_mapper;

import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

class RgbFloatEncoderNative {
    static {
        NativeLibLoader.ensureLoaded();
    }

    /**
     * 从快照纹理解码 RGB float 到 FloatVector（CPU侧异步，GPU侧顺序执行）。
     * native 侧无持久资源：Map→kernel→Unmap 每次调用内完成，texObj 即建即毁。
     *
     * @param snapshotTextureHandle 快照 Texture 对象的 C++ 指针 (Texture*，bridge 内读尺寸与 cudaResource)
     * @param elementOffset         在 FloatVector 中的起始元素偏移量
     * @param streamHandle          CUDA 流句柄
     * @param dstVectorHandle       目标 FloatVector 的设备指针句柄
     */
    static native void _encode(
            long snapshotTextureHandle,
            long elementOffset,
            long streamHandle,
            /*->*/ long dstVectorHandle);
}
