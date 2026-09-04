// cuda_gl_vision.h
// 视觉解码 kernel 的启动入口（新版分支）：快照纹理（GL 纹理）→ NN 家族载体。
// 位平面版服务 BNN 载体（BoolVector），RGB float 版服务 CNN 载体（FloatVector）。
// 参数序遵循设计原则第5条（DPS）：左边入参，右边出参，/* -> */ 分隔。
#pragma once

#include <cuda_runtime.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// 启动位平面解码 kernel（BNN 载体）：RGBA 纹理 → 24 个位平面（R0-R7, G0-G7, B0-B7）
// texObj:        从 cudaArray 创建的 texture object（整数坐标、最近邻采样）
// wordsPerPlane: 每平面 uint32_t 数 = ceil(width * height / 32)
// stream:        CUDA stream，0 表示默认流
// out:           BoolVector device data (uint32_t*)，已按 32 位 word 对齐
cudaError_t launchUnpackKernel(
    cudaTextureObject_t texObj,
    int outWidth,
    int outHeight,
    int inWidth,
    int inHeight,
    int wordsPerPlane,
    cudaStream_t stream,
    /* -> */ uint32_t* out
);

// 启动 RGB float 解码 kernel（CNN 载体）：RGBA 纹理 → 通道平面式 RGB float
// （R 平面 | G 平面 | B 平面，各 w*h 元素，值域 [0,1]）
// texObj:  从 cudaArray 创建的 texture object（整数坐标、最近邻采样）
// stream:  CUDA stream，0 表示默认流
// out:     FloatVector device data (float*)
cudaError_t launchRgbFloatKernel(
    cudaTextureObject_t texObj,
    int width,
    int height,
    cudaStream_t stream,
    /* -> */ float* out
);

#ifdef __cplusplus
}
#endif
