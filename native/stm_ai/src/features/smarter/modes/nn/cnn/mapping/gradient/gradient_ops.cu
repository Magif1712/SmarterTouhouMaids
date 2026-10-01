#include "gradient_ops.h"
#include <cstdio>
#include <cstdlib>
#include <stdint.h>

constexpr int BLOCK = 256;

// σ'(z) = y(1-y)，直接用 trace_y 计算，无需重算 σ。
__device__ __forceinline__ float cnn_sigmoid_deriv_from_y(float y) {
    return y * (1.0f - y);
}

// Kernel1（sizeA1）：计算 δ 并更新 buf_l/r/b。
// δ_j = 2(y_j - target_j) · y_j(1-y_j)
// ∂l_j = δ_j · x[j-1]（j-1<0 或 >=sizeA0 时 x=0）
// ∂r_j = δ_j · x[j+1]（j+1>=sizeA0 时 x=0）
// ∂b_j = δ_j
__global__ void __launch_bounds__(BLOCK, 2)
cnn_backward_delta_kernel(
    const float* __restrict__ trace_y,
    const float* __restrict__ target,
    const float* __restrict__ x,
    int sizeA0, int sizeA1, float lr /* -> */,
    float* __restrict__ buf_l,
    float* __restrict__ buf_r,
    float* __restrict__ buf_b,
    float* __restrict__ dz)
{
    int j = blockIdx.x * blockDim.x + threadIdx.x;
    if (j >= sizeA1) return;

    float yj = __ldg(&trace_y[j]);
    float tj = __ldg(&target[j]);
    float dy = yj - tj;                        // (y - target)
    float sd = cnn_sigmoid_deriv_from_y(yj);   // y(1-y)
    float delta = 2.0f * dy * sd;              // δ = 2(y-target)·y(1-y)

    dz[j] = delta;

    // ∂l_j = δ · x[j-1]（边界补0）
    float xl = 0.0f;
    if (j - 1 >= 0 && j - 1 < sizeA0) {
        xl = __ldg(&x[j - 1]);
    }
    // ∂r_j = δ · x[j+1]（边界补0）
    float xr = 0.0f;
    if (j + 1 < sizeA0) {
        xr = __ldg(&x[j + 1]);
    }
    // ∂b_j = δ
    float grad_l = delta * xl;
    float grad_r = delta * xr;
    float grad_b = delta;

    buf_l[j] = buf_l[j] - lr * grad_l;
    buf_r[j] = buf_r[j] - lr * grad_r;
    buf_b[j] = buf_b[j] - lr * grad_b;
}

// Kernel2（sizeA0）：计算 dInput + bufTc + 更新 buf_p/q + 刷新 buf_idx/w。
// ∂x_i = Σ_k δ[idx_k[i]]·q_i·w_k[i] + δ[i+1]·l[i+1]·[i+1<sizeA1] + δ[i-1]·r[i-1]·[i-1>=0]
// ∂p_i = Σ_k δ[idx_k[i]]·x_i·q_i·(-2·d_k)，d_k = hp_p_i - hp_idx_k[i]
// ∂q_i = Σ_k δ[idx_k[i]]·x_i·w_k[i]
// 权重更新后 clamp buf_p[0, sizeA1-1]，刷新 buf_idx0/idx1/w0/w1（同 refresh_cache_kernel 逻辑）。
// StoreTc=true 时写 bufTc（GradCellOp 阶段一外拷输入梯度）；
// StoreTc=false 时跳过（阶段二只更新权重）。编译期消除，无运行时分支。
//
// 注意：hp_p/q/idx/w 可能与 buf_p/q/idx/w 别名（bufHp==hp 时）。
// 不对 hp_p/q/idx/w 用 __ldg（可能被同 kernel 写入导致 texture cache 过期）。
// dz/x/hp_l/hp_r 只读且不与任何写别名，安全用 __ldg。
template <bool StoreTc>
__global__ void __launch_bounds__(BLOCK, 2)
cnn_backward_input_pq_kernel(
    const float* __restrict__ dz,
    const float* __restrict__ x,
    const float* __restrict__ hp_p,
    const float* __restrict__ hp_q,
    const float* __restrict__ hp_l,
    const float* __restrict__ hp_r,
    const int* __restrict__ hp_idx0,
    const int* __restrict__ hp_idx1,
    const float* __restrict__ hp_w0,
    const float* __restrict__ hp_w1,
    int sizeA0, int sizeA1, int sizeC, float lr /* -> */,
    float* buf_p,
    float* buf_q,
    int* buf_idx0,
    int* buf_idx1,
    float* buf_w0,
    float* buf_w1,
    float* __restrict__ dInput,
    float* __restrict__ bufTc)
{
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= sizeA0) return;

    float xi = __ldg(&x[i]);
    float qi = __ldg(&hp_q[i]);
    int j0 = __ldg(&hp_idx0[i]);
    int j1 = __ldg(&hp_idx1[i]);
    float w0 = __ldg(&hp_w0[i]);
    float w1 = __ldg(&hp_w1[i]);

    // ∂x_i: push 项
    float dx = 0.0f;
    if (j0 >= 0) {
        dx += __ldg(&dz[j0]) * qi * w0;
    }
    if (j1 >= 0) {
        dx += __ldg(&dz[j1]) * qi * w1;
    }
    // ∂x_i: l 项（δ[i+1]·l[i+1]，i+1 < sizeA1）
    if (i + 1 < sizeA1) {
        dx += __ldg(&dz[i + 1]) * __ldg(&hp_l[i + 1]);
    }
    // ∂x_i: r 项（δ[i-1]·r[i-1]，i-1 >= 0）
    if (i - 1 >= 0) {
        dx += __ldg(&dz[i - 1]) * __ldg(&hp_r[i - 1]);
    }

    dInput[i] = dx;
    if constexpr (StoreTc) {
        if (i < sizeC) {
            bufTc[i] = dx;
        }
    }

    float pi = hp_p[i];

    // ∂p_i = Σ_k δ[idx_k[i]]·x_i·q_i·(-2·d_k)
    // ∂q_i = Σ_k δ[idx_k[i]]·x_i·w_k[i]
    float dp = 0.0f;
    float dq = 0.0f;
    if (j0 >= 0) {
        float d0 = pi - (float)j0;
        dp += __ldg(&dz[j0]) * xi * qi * (-2.0f * d0);
        dq += __ldg(&dz[j0]) * xi * w0;
    }
    if (j1 >= 0) {
        float d1 = pi - (float)j1;
        dp += __ldg(&dz[j1]) * xi * qi * (-2.0f * d1);
        dq += __ldg(&dz[j1]) * xi * w1;
    }

    // 权重更新: buf_p -= lr·∂p; buf_q -= lr·∂q
    float new_p = buf_p[i] - lr * dp;
    float new_q = buf_q[i] - lr * dq;

    // clamp buf_p[0, sizeA1-1]
    if (new_p < 0.0f) new_p = 0.0f;
    float maxP = (float)(sizeA1 - 1);
    if (new_p > maxP) new_p = maxP;

    buf_p[i] = new_p;
    buf_q[i] = new_q;

    // 刷新 buf_idx0/idx1/w0/w1 from buf_p（同 refresh_cache_kernel 逻辑）
    int nj0 = __float2int_rd(new_p);
    int nj1 = nj0 + 1;
    float nd0 = new_p - (float)nj0;
    float nd1 = new_p - (float)nj1;

    if (nj0 >= 0 && nj0 < sizeA1 && fabsf(nd0) < 1.0f) {
        buf_idx0[i] = nj0;
        buf_w0[i] = 1.0f - nd0 * nd0;
    } else {
        buf_idx0[i] = -1;
        buf_w0[i] = 0.0f;
    }
    if (nj1 >= 0 && nj1 < sizeA1 && fabsf(nd1) < 1.0f) {
        buf_idx1[i] = nj1;
        buf_w1[i] = 1.0f - nd1 * nd1;
    } else {
        buf_idx1[i] = -1;
        buf_w1[i] = 0.0f;
    }
}

extern "C" void cnn_backward_layer(
    const float* trace_z, const float* trace_y, const float* target, const float* x,
    const float* hp_p, const float* hp_q, const float* hp_l, const float* hp_r, const float* hp_b,
    const int* hp_idx0, const int* hp_idx1, const float* hp_w0, const float* hp_w1,
    int sizeA0, int sizeA1, int sizeC, float lr, cudaStream_t stream /* -> */,
    float* buf_p, float* buf_q, float* buf_l, float* buf_r, float* buf_b,
    int* buf_idx0, int* buf_idx1, float* buf_w0, float* buf_w1,
    float* dz, float* dInput, float* bufTc)
{
    // trace_z 接收但不用（反向用 trace_y 算 σ'=y(1-y)）。
    // hp_b 接收但不用（∂b_j=δ_j 不依赖 b 值）。
    (void)trace_z;
    (void)hp_b;

    if (trace_y == nullptr || target == nullptr || x == nullptr || dz == nullptr || dInput == nullptr) {
        fprintf(stderr, "ERROR: null required pointer in cnn_backward_layer\n");
        abort();
    }

    dim3 block(BLOCK);
    dim3 grid1((unsigned int)((sizeA1 + BLOCK - 1) / BLOCK));
    dim3 grid2((unsigned int)((sizeA0 + BLOCK - 1) / BLOCK));

    // Kernel1: δ + buf_l/r/b 更新（同 stream，先于 Kernel2 完成）
    cnn_backward_delta_kernel<<<grid1, block, 0, stream>>>(
        trace_y, target, x, sizeA0, sizeA1, lr /* -> */, buf_l, buf_r, buf_b, dz);

    // Kernel2: dInput + bufTc + buf_p/q 更新 + buf_idx/w 刷新
    // （读 dz 由 Kernel1 写入，同 stream 保证 Kernel1→Kernel2 顺序）
    // bufTc 非空时实例化 <true>（写 bufTc），为空时 <false>（跳过），编译期消除分支。
    if (bufTc != nullptr) {
        cnn_backward_input_pq_kernel<true><<<grid2, block, 0, stream>>>(
            dz, x, hp_p, hp_q, hp_l, hp_r, hp_idx0, hp_idx1, hp_w0, hp_w1,
            sizeA0, sizeA1, sizeC, lr /* -> */, buf_p, buf_q, buf_idx0, buf_idx1, buf_w0, buf_w1, dInput, bufTc);
    } else {
        cnn_backward_input_pq_kernel<false><<<grid2, block, 0, stream>>>(
            dz, x, hp_p, hp_q, hp_l, hp_r, hp_idx0, hp_idx1, hp_w0, hp_w1,
            sizeA0, sizeA1, sizeC, lr /* -> */, buf_p, buf_q, buf_idx0, buf_idx1, buf_w0, buf_w1, dInput, nullptr);
    }
}