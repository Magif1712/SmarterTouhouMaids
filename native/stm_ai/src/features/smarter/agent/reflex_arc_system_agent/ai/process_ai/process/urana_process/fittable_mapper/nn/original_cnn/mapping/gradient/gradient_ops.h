#pragma once
#include <cuda_runtime.h>
#include <cstdint>

// CNN 反向传播两阶段 host 入口（单函数内 launch 两个 kernel，同 stream 保证顺序）。
//
// Kernel1（sizeA1）：δ_j=2(y-target)·y(1-y)→dz[j]；
//   ∂l_j=δ·x[j-1]（边界补0）；∂r_j=δ·x[j+1]（边界补0）；∂b_j=δ；
//   buf_l/r/b -= lr·grad。
//
// Kernel2（sizeA0）：∂x_i=Σ_k δ[idx_k[i]]·q·w_k + δ[i+1]·l[i+1] + δ[i-1]·r[i-1]→dInput+bufTc；
//   ∂p_i=Σ_k δ[idx_k[i]]·x·q·(-2·d_k)，d_k=p-idx_k[i]；
//   ∂q_i=Σ_k δ[idx_k[i]]·x·w_k；
//   buf_p/q -= lr·grad；clamp buf_p[0,sizeA1-1]；刷新 buf_idx0/idx1/w0/w1。
//
// 始终更新权重 buf_* + 刷新 cache（GradCellOp 两阶段都更新）。
// bufTc 为 nullptr 时跳过外拷输入梯度。
//
// trace_z 接收但不用（反向用 trace_y 的 y(1-y) 算 σ'，无需重算 σ）。
// hp_b 接收但不用（∂b_j=δ_j，不依赖 b 值）。
extern "C" void cnn_backward_layer(
    const float* trace_z, const float* trace_y, const float* target, const float* x,
    const float* hp_p, const float* hp_q, const float* hp_l, const float* hp_r, const float* hp_b,
    const int* hp_idx0, const int* hp_idx1, const float* hp_w0, const float* hp_w1,
    int sizeA0, int sizeA1, int sizeC, float lr, cudaStream_t stream /* -> */,
    float* buf_p, float* buf_q, float* buf_l, float* buf_r, float* buf_b,
    int* buf_idx0, int* buf_idx1, float* buf_w0, float* buf_w1,
    float* dz, float* dInput, float* bufTc);
