package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.vision;

import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

class VisionNative {
    static {
        NativeLibLoader.ensureLoaded();
    }

    /**
     * 通过快照机制捕获屏幕到 BoolVector（CPU侧异步，GPU侧顺序执行）。
     *
     * @param textureId           源纹理 OpenGL ID
     * @param tempTextureHandle   临时 Texture 对象的 C++ 指针 (Texture*)
     * @param tempTextureOpenGLId 临时纹理的 OpenGL 纹理 ID
     * @param tempCudaResourceHandle 临时纹理的 CUDA 资源句柄 (intptr_t)
     * @param handle              目标 BoolVector 的设备指针句柄
     * @param bitOffset           在 BoolVector 中的起始位偏移量
     * @param texWidth            源纹理宽度
     * @param texHeight           源纹理高度
     * @param aiWidth             AI 视网膜宽度
     * @param aiHeight            AI 视网膜高度
     * @param stream_handle       CUDA 流句柄
     */
    static native void _captureScreenViaSnapshot(
            int textureId,
            long tempTextureHandle,
            int tempTextureOpenGLId,
            long tempCudaResourceHandle,
            long handle,
            long bitOffset,
            int texWidth,
            int texHeight,
            int aiWidth,
            int aiHeight,
            long stream_handle
    );

    /**
     * 清理 Vision Bridge 使用的所有原生资源。
     */
    static native void _cleanup();
}