#pragma once

// Windows 上 GL/gl.h 依赖 windows.h 定义的 APIENTRY/WINGDIAPI 等宏
// 否则 GLuint/GLEnum 不会被 typedef，cuda_gl_interop.h 会炸
#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#endif

#include <cuda_runtime.h>

#ifdef __cplusplus

template <typename T>
class Vector;

class Texture;

extern "C" {
#endif

/**
 * RGB float 解码（从快照纹理到 FloatVector，CPU侧异步，GPU侧顺序执行）。
 *
 * 只做解码（Map → GetArray → CreateTexObj → kernel → Destroy → Unmap）；
 * 快照采集（glBlit 深拷贝）归 Texture::snapshotFrom（core 原语），由 Java 侧
 * PossessionSensor 在解码前先行调用。采集与解码分离：采集每帧，解码按消费（拉模型）。
 *
 * 输出布局：通道平面式（R 平面 | G 平面 | B 平面，各 w*h 元素），值域 [0,1]
 * （v/255 归一化）——与位平面编码器的平面优先约定一致，CNN（浮点激活网络）载体。
 *
 * 解码经由快照纹理（GL 纹理）：Map 等操作插入 OpenGL 命令序列（GL 单一命令流 = 渲染流），
 * 与渲染串行；解码由 AI 消费挂起，频率与 AI 匹配。
 *
 * 解码区域 = 快照纹理全域（尺寸由纹理自取，纹理恒为 AI 视网膜尺寸）。
 *
 * @param snapshotTexture 快照纹理（已深拷贝填充的独立副本）
 * @param elementOffset   元素偏移量（float 元素）
 * @param stream          CUDA 流
 * @param dst_vector      目标 FloatVector
 * @return cudaError_t 错误码（即使返回 success，kernel 可能仍在执行）
 */
cudaError_t rgbFloatEncodeBridge(
    Texture* snapshotTexture,
    size_t elementOffset,
    cudaStream_t stream,
    /* -> */ Vector<float>* dst_vector
);

#ifdef __cplusplus
}
#endif
