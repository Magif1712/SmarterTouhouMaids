#include <cuda_runtime.h>
#include <cstdio>  // 提供 fprintf, stderr
#include <cstdlib> // 提供 abort
#include <stdint.h>

constexpr uint32_t BITS = 32;
constexpr uint32_t LOG_BITS = 5;
constexpr int BLOCK = 256;

__global__ void __launch_bounds__(BLOCK, 2)
    bnn_push_p_original(
        const uint32_t *__restrict__ a_prev,
        const uint32_t *__restrict__ q,
        uint32_t *__restrict__ a_curr,
        const int32_t *__restrict__ P,
        size_t n,
        size_t n_out_words)
{
    size_t i = (size_t)blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= n)
        return;

    size_t wi = i >> LOG_BITS;
    uint32_t bit = 1u << (i & (BITS - 1));

    if ((__ldg(&a_prev[wi]) & __ldg(&q[wi]) & bit) == 0)
        return;

    int32_t j = __ldg(&P[i]);
    // 防御性上界检查：j 是 a_curr 的比特索引，必须在 [0, n_out_words * 32) 内。
    // 若 P 未初始化或损坏导致 j 越界，atomicOr 会写越界触发 illegal memory access，
    // 使整个 CUDA 上下文失效（后续所有 op 误报同一 sticky 错误）。
    if (j < 0 || (size_t)j >= (size_t)n_out_words * BITS)
        return;

    atomicOr(&a_curr[j >> LOG_BITS], 1u << (j & (BITS - 1)));
}

// 模板 bool 特化（推荐）
template <bool StoreFZ>
__global__ void __launch_bounds__(BLOCK, 2)
    bnn_pull_lr_original(
        const uint32_t *__restrict__ a_prev_pad,
        uint32_t *__restrict__ a_curr,
        uint32_t *__restrict__ fz,
        const uint32_t *__restrict__ l,
        const uint32_t *__restrict__ r,
        const uint32_t *__restrict__ b,
        size_t n_words,
        size_t input_n_words)
{
    int64_t w = (int64_t)blockIdx.x * blockDim.x + threadIdx.x;
    if (w < 0 || w >= (int64_t)n_words)
        return;

    uint32_t prev  = (w < input_n_words)                      ? __ldg(&a_prev_pad[w])   : 0;
    uint32_t left  = (w > 0 && w - 1 < input_n_words)         ? __ldg(&a_prev_pad[w - 1]) : 0;
    uint32_t right = (w + 1 < input_n_words)                   ? __ldg(&a_prev_pad[w + 1]) : 0;
    uint32_t l_val = (w < input_n_words)                       ? __ldg(&l[w])            : 0;
    uint32_t r_val = (w < input_n_words)                       ? __ldg(&r[w])            : 0;
    uint32_t out = a_curr[w];

    out |= __funnelshift_r(left, prev, 31) & l_val;
    out |= __funnelshift_r(prev, right, 1) & r_val;

    // 不要删掉constexpr，constexpr是编译时常量，允许编译器在编译阶段优化代码路径，删了就要在运行时分支了，对挺大地降低性能的
    if constexpr (StoreFZ)
    {
        __stcg(&fz[w], out);
    }

    out ^= __ldg(&b[w]);
    a_curr[w] = out;
}

extern "C" void bnn_forward_layer_storefz_original(
    const uint32_t *a_prev_pad, const uint32_t *q, const int32_t *P,
    const uint32_t *l, const uint32_t *r, const uint32_t *b,
    uint32_t *a_curr, uint32_t *fz, size_t n, size_t n_words,
    cudaStream_t stream)
{
    // 强制崩溃，防止空指针传入
    if (fz == nullptr)
    {
        fprintf(stderr, "ERROR: fz is null in bnn_forward_layer_store_fz_original\n");
        abort(); // 立即崩溃
    }

    size_t input_n_words = (n + 31) / 32;

    cudaMemsetAsync(a_curr, 0, n_words * sizeof(uint32_t), stream);

    dim3 block(BLOCK);
    dim3 grid_push((unsigned int)((n + BLOCK - 1) / BLOCK));
    dim3 grid_pull((unsigned int)((n_words + BLOCK - 1) / BLOCK));

    bnn_push_p_original<<<grid_push, block, 0, stream>>>(a_prev_pad, q, a_curr, P, n, n_words);
    bnn_pull_lr_original<true><<<grid_pull, block, 0, stream>>>(a_prev_pad, a_curr, fz, l, r, b, n_words, input_n_words);
}

extern "C" void bnn_forward_layer_nofz_original(
    const uint32_t *a_prev_pad, const uint32_t *q, const int32_t *P,
    const uint32_t *l, const uint32_t *r, const uint32_t *b,
    uint32_t *a_curr, size_t n, size_t n_words,
    cudaStream_t stream)
{
    size_t input_n_words = (n + 31) / 32;

    cudaMemsetAsync(a_curr, 0, n_words * sizeof(uint32_t), stream);

    dim3 block(BLOCK);
    dim3 grid_push((unsigned int)((n + BLOCK - 1) / BLOCK));
    dim3 grid_pull((unsigned int)((n_words + BLOCK - 1) / BLOCK));

    bnn_push_p_original<<<grid_push, block, 0, stream>>>(a_prev_pad, q, a_curr, P, n, n_words);
    bnn_pull_lr_original<false><<<grid_pull, block, 0, stream>>>(a_prev_pad, a_curr, nullptr, l, r, b, n_words, input_n_words);
}
