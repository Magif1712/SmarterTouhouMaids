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
 * 位平面解码（从快照纹理到 BoolVector，CPU侧异步，GPU侧顺序执行）。
 *
 * 只做解码（Map → GetArray → CreateTexObj → kernel → Destroy → Unmap）；
 * 快照采集（glBlit 深拷贝）归 Texture::snapshotFrom（core 原语），由 Java 侧
 * PossessionSensor 在解码前先行调用。采集与解码分离：采集每帧，解码按消费（拉模型）。
 *
 * 解码经由快照纹理（GL 纹理）：Map 等操作插入 OpenGL 命令序列（GL 单一命令流 = 渲染流），
 * 与渲染串行；解码由 AI 消费挂起，频率与 AI 匹配。
 *
 * 解码区域 = 快照纹理全域（尺寸由纹理自取，纹理恒为 AI 视网膜尺寸）。
 *
 * @param snapshotTexture 快照纹理（已深拷贝填充的独立副本）
 * @param bitOffset       位偏移量（32 位对齐）
 * @param stream          CUDA 流
 * @param dst_vector      目标 BoolVector
 * @return cudaError_t 错误码（即使返回 success，kernel 可能仍在执行）
 */
cudaError_t bitplaneEncodeBridge(
    Texture* snapshotTexture,
    size_t bitOffset,
    cudaStream_t stream,
    /* -> */ Vector<bool>* dst_vector
);

#ifdef __cplusplus
}
#endif
