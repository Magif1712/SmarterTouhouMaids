#pragma once
#include <cuda_runtime.h>
#include <stdint.h>

// CNN 前向（带 trace，训练用）：z=trace_z 累加（push atomicAdd + pull_lr + b），
// y=trace_y 写 σ(z)。前向用 idx/w（持久缓存），不用 p（p 仅 refreshCache/backward 用）。
extern "C" void cnn_forward_layer_trace(
    const float* x, const float* q, const float* l, const float* r, const float* b,
    const int* idx0, const int* idx1, const float* w0, const float* w1,
    int sizeA0, int sizeA1, cudaStream_t stream /* -> */,
    float* y, float* trace_z, float* trace_y);

// CNN 前向（无 trace，纯推理）：y 做工作区（push→pull→activate 覆盖 y）。
extern "C" void cnn_forward_layer_notrace(
    const float* x, const float* q, const float* l, const float* r, const float* b,
    const int* idx0, const int* idx1, const float* w0, const float* w1,
    int sizeA0, int sizeA1, cudaStream_t stream /* -> */,
    float* y);

// CNN 缓存刷新：由 p 重算 idx0/idx1/w0/w1（非热路径，构造/loadFromFile 后一次性）。
extern "C" void cnn_refresh_cache(
    const float* p, int sizeA0, int sizeA1, cudaStream_t stream /* -> */,
    int* idx0, int* idx1, float* w0, float* w1);
