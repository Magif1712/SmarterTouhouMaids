#include "inference_ops.h"
#include <cstdio>
#include <cstdlib>
#include <stdint.h>

constexpr int BLOCK = 256;

// σ(z) = 1/(1+__expf(-z))。__expf 为 fast math（牺牲精度换性能，CNN 训练可接受）。
__device__ __forceinline__ float cnn_sigmoid(float z) {
    return 1.0f / (1.0f + __expf(-z));
}

// push kernel: z[idx_k[i]] += q[i]*w_k[i]*x[i] (atomicAdd float)。
// 稀疏 2 连接：每个输入 i 投影到 0/1/2 个输出位（idx=-1 跳过）。
__global__ void __launch_bounds__(BLOCK, 2)
cnn_push_kernel(const float* __restrict__ x, const float* __restrict__ q, const int* __restrict__ idx0, const int* __restrict__ idx1, const float* __restrict__ w0, const float* __restrict__ w1, int sizeA0 /* -> */, float* __restrict__ z) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= sizeA0) return;

    float xi = __ldg(&x[i]);
    float qi = __ldg(&q[i]);
    int j0 = __ldg(&idx0[i]);
    int j1 = __ldg(&idx1[i]);

    if (j0 >= 0) {
        atomicAdd(&z[j0], qi * __ldg(&w0[i]) * xi);
    }
    if (j1 >= 0) {
        atomicAdd(&z[j1], qi * __ldg(&w1[i]) * xi);
    }
}

// pull_lr_activate kernel (模板 StoreTrace)。
// StoreTrace=true: z=trace_z, y=trace_y（不同 buffer）；z[j] 保留累加结果，y[j]=σ(z)。
// StoreTrace=false: z=y=output（同一 buffer）；σ 覆盖 z（不保留累加结果）。
// l[j]*x[j-1] / r[j]*x[j+1]: j-1/j+1 作为输入索引，越界（<0 或 >=sizeA0）补 0。
template <bool StoreTrace>
__global__ void __launch_bounds__(BLOCK, 2)
cnn_pull_lr_activate_kernel(const float* __restrict__ x, const float* __restrict__ l, const float* __restrict__ r, const float* __restrict__ b, int sizeA0, int sizeA1 /* -> */, float* z, float* y) {
    int j = blockIdx.x * blockDim.x + threadIdx.x;
    if (j >= sizeA1) return;

    float zj = z[j];
    if (j - 1 >= 0 && j - 1 < sizeA0) {
        zj += __ldg(&l[j]) * __ldg(&x[j - 1]);
    }
    if (j + 1 < sizeA0) {
        zj += __ldg(&r[j]) * __ldg(&x[j + 1]);
    }
    zj += __ldg(&b[j]);

    if constexpr (StoreTrace) {
        z[j] = zj;
        y[j] = cnn_sigmoid(zj);
    } else {
        // NoTrace: z==y 同一 buffer，σ 直接覆盖
        y[j] = cnn_sigmoid(zj);
    }
}

// refresh_cache kernel: 由 p 重算 idx0/idx1/w0/w1。
// j0=floor(p), j1=j0+1; idx_k=j_k if (0<=j_k<sizeA1 且 |p-j_k|<1) else -1; w_k=1-(p-j_k)^2。
__global__ void __launch_bounds__(BLOCK, 2)
cnn_refresh_cache_kernel(const float* __restrict__ p, int sizeA0, int sizeA1 /* -> */, int* __restrict__ idx0, int* __restrict__ idx1, float* __restrict__ w0, float* __restrict__ w1) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= sizeA0) return;

    float pi = __ldg(&p[i]);
    int j0 = __float2int_rd(pi);
    int j1 = j0 + 1;
    float d0 = pi - (float)j0;
    float d1 = pi - (float)j1;

    if (j0 >= 0 && j0 < sizeA1 && fabsf(d0) < 1.0f) {
        idx0[i] = j0;
        w0[i] = 1.0f - d0 * d0;
    } else {
        idx0[i] = -1;
        w0[i] = 0.0f;
    }
    if (j1 >= 0 && j1 < sizeA1 && fabsf(d1) < 1.0f) {
        idx1[i] = j1;
        w1[i] = 1.0f - d1 * d1;
    } else {
        idx1[i] = -1;
        w1[i] = 0.0f;
    }
}

extern "C" void cnn_forward_layer_trace(
    const float* x, const float* q, const float* l, const float* r, const float* b,
    const int* idx0, const int* idx1, const float* w0, const float* w1,
    int sizeA0, int sizeA1, cudaStream_t stream /* -> */,
    float* y, float* trace_z, float* trace_y) {

    if (trace_z == nullptr || trace_y == nullptr) {
        fprintf(stderr, "ERROR: trace_z/trace_y is null in cnn_forward_layer_trace\n");
        abort();
    }

    cudaMemsetAsync(trace_z, 0, sizeA1 * sizeof(float), stream);

    dim3 block(BLOCK);
    dim3 grid_push((unsigned int)((sizeA0 + BLOCK - 1) / BLOCK));
    dim3 grid_pull((unsigned int)((sizeA1 + BLOCK - 1) / BLOCK));

    cnn_push_kernel<<<grid_push, block, 0, stream>>>(x, q, idx0, idx1, w0, w1, sizeA0 /* -> */, trace_z);
    cnn_pull_lr_activate_kernel<true><<<grid_pull, block, 0, stream>>>(x, l, r, b, sizeA0, sizeA1 /* -> */, trace_z, trace_y);
}

extern "C" void cnn_forward_layer_notrace(
    const float* x, const float* q, const float* l, const float* r, const float* b,
    const int* idx0, const int* idx1, const float* w0, const float* w1,
    int sizeA0, int sizeA1, cudaStream_t stream /* -> */,
    float* y) {

    cudaMemsetAsync(y, 0, sizeA1 * sizeof(float), stream);

    dim3 block(BLOCK);
    dim3 grid_push((unsigned int)((sizeA0 + BLOCK - 1) / BLOCK));
    dim3 grid_pull((unsigned int)((sizeA1 + BLOCK - 1) / BLOCK));

    cnn_push_kernel<<<grid_push, block, 0, stream>>>(x, q, idx0, idx1, w0, w1, sizeA0 /* -> */, y);
    cnn_pull_lr_activate_kernel<false><<<grid_pull, block, 0, stream>>>(x, l, r, b, sizeA0, sizeA1 /* -> */, y, y);
}

extern "C" void cnn_refresh_cache(
    const float* p, int sizeA0, int sizeA1, cudaStream_t stream /* -> */,
    int* idx0, int* idx1, float* w0, float* w1) {

    dim3 block(BLOCK);
    dim3 grid((unsigned int)((sizeA0 + BLOCK - 1) / BLOCK));

    cnn_refresh_cache_kernel<<<grid, block, 0, stream>>>(p, sizeA0, sizeA1 /* -> */, idx0, idx1, w0, w1);
}
