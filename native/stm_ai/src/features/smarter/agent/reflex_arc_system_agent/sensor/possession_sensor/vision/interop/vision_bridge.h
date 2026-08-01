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
#include <GL/gl.h>

#ifdef __cplusplus

template <typename T>
class Vector;

class Texture;

extern "C" {
#endif

/**
 * 通过快照机制捕获屏幕到 BoolVector（CPU侧异步，GPU侧顺序执行）。
 *
 * 与旧版 captureScreenToBoolVectorBridge 的区别：
 * - 使用预分配的 Texture 对象进行快照隔离（避免 OpenGL-CUDA 竞态）
 * - 不调用 cudaStreamSynchronize（CPU 侧异步返回）
 * - 调用方负责在需要数据时确保 stream 同步
 *
 * @param srcTextureId      源纹理 OpenGL ID
 * @param tempTexture       预分配的临时 Texture 对象（由 Java 侧池化管理）
 * @param dst_vector        目标 BoolVector
 * @param bitOffset         位偏移量
 * @param texWidth          源纹理宽度
 * @param texHeight         源纹理高度
 * @param aiWidth           AI 视网膜宽度
 * @param aiHeight          AI 视网膜高度
 * @param stream            CUDA 流
 * @return cudaError_t 错误码（即使返回 success，kernel 可能仍在执行）
 */
cudaError_t captureScreenViaSnapshotBridge(
    GLuint srcTextureId,
    Texture* tempTexture,
    Vector<bool>* dst_vector,
    size_t bitOffset,
    int texWidth,
    int texHeight,
    int aiWidth,
    int aiHeight,
    cudaStream_t stream
);

void cleanupVisionBridge();

#ifdef __cplusplus
}
#endif