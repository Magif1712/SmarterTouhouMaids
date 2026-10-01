#include "bnn_ops.h"

// ==============================================
// negateAndBinarizeKernel
//
// 统一核函数：同时支持全数组和子区间。
//
// 每个 warp 负责一个 dst word（32 bit）。
// lane i 计算该 word 内第 i 个 bit 对应的 src 元素，
// 通过 __ballot_sync 打包成 mask，lane 0 写回。
//
// 当 dst_bit_offset=0, src_offset=0 时，完全退化为原逻辑。
// ==============================================
__global__ void negateAndBinarizeKernel_original(
    const int*      __restrict__ src,
    size_t          src_offset,
    uint32_t*       __restrict__ dst_packed,
    size_t          dst_bit_offset,
    size_t          n)
{
    const size_t tid  = (size_t)blockIdx.x * blockDim.x + threadIdx.x;
    const int lane    = threadIdx.x & (warpSize - 1);

    // --- 1. 本 warp 负责哪个 dst word ---
    const size_t first_word     = dst_bit_offset / 32;
    const size_t last_word_excl = (dst_bit_offset + n + 31) / 32;
    const size_t num_words      = last_word_excl - first_word;
    const size_t warp_idx       = tid / 32;

    if (warp_idx >= num_words) return;

    const size_t dst_word       = first_word + warp_idx;
    const size_t word_start_bit = dst_word * 32;

    // --- 2. lane i 对应的 dst bit，反推 src 索引 ---
    const size_t my_global_bit  = word_start_bit + lane;
    const int64_t src_rel       = (int64_t)my_global_bit - (int64_t)dst_bit_offset;

    const bool in_range = (src_rel >= 0) && (src_rel < (int64_t)n);
    const size_t src_idx = in_range ? (src_offset + (size_t)src_rel) : 0;

    // --- 3. ballot 打包（与原 kernel 完全一致）---
    const bool is_negative = in_range ? (__ldg(&src[src_idx]) < 0) : false;
    const uint32_t bitmask = __ballot_sync(0xFFFFFFFF, is_negative);

    // --- 4. lane 0 写入（热路径直接写，冷路径 RMW）---
    if (lane == 0) {
        const size_t op_start = dst_bit_offset;
        const size_t op_end   = dst_bit_offset + n;

        const bool fully_covered = (word_start_bit >= op_start) &&
                                   ((word_start_bit + 32) <= op_end);

        if (fully_covered) {
            // 热路径：绝大多数 word 走这里，直接覆盖
            dst_packed[dst_word] = bitmask;
        } else {
            // 冷路径：首尾不完整的 word，read-modify-write
            const uint32_t old = dst_packed[dst_word];

            const int valid_start = (int)max((int64_t)0,
                                             (int64_t)op_start - (int64_t)word_start_bit);
            const int valid_end   = (int)min((int64_t)32,
                                             (int64_t)op_end   - (int64_t)word_start_bit);

            uint32_t mask = 0;
            if (valid_start < valid_end) {
                const uint32_t lo_mask = (valid_start == 0) ? 0u
                                          : ((1u << valid_start) - 1u);
                const uint32_t hi_mask = (valid_end == 32) ? 0xFFFFFFFFu
                                          : ((1u << valid_end) - 1u);
                mask = hi_mask & ~lo_mask;
            }

            dst_packed[dst_word] = (old & ~mask) | (bitmask & mask);
        }
    }
}

// ==============================================
// 主机端辅助：计算 grid 维度
// ==============================================
__host__ static inline dim3 calcGrid(size_t num_words, int block_size)
{
    const size_t total_threads = num_words * 32;   // 每个 word 一个 warp
    const size_t grid = (total_threads + block_size - 1) / block_size;
    return dim3((unsigned int)grid);
}

// ==============================================
// negateAndBinarize
//
// 对 src[0..n) 做 negate-and-binarize，结果写入 dst_packed[0..n)。
//
// 注意：本函数不自动清零未使用位。若 dst 非全新分配，
// 且 n 不是 32 的倍数，调用方应自行确保尾部已初始化。
// ==============================================
cudaError_t negateAndBinarize_original(
    uint32_t*       __restrict__ dst_packed,
    const int*      __restrict__ src,
    size_t          n,
    cudaStream_t    stream)
{
    if (n == 0) {
        return cudaSuccess;
    }
    if (dst_packed == nullptr || src == nullptr) {
        return cudaErrorInvalidValue;
    }

    constexpr int block_size = 256;
    negateAndBinarizeKernel_original<<<calcGrid((n + 31) / 32, block_size),
                              block_size, 0, stream>>>(
        src, 0, dst_packed, 0, n);

    return cudaGetLastError();
}

// ==============================================
// negateAndBinarizeRegion
//
// 对 src[src_offset .. src_offset+n) 做 negate-and-binarize，
// 结果按位写入 dst_packed 从 dst_bit_offset 开始的 n 个 bit。
//
// 保证：不覆盖 dst 中不属于 [dst_bit_offset, dst_bit_offset+n) 的 bit。
// ==============================================
cudaError_t negateAndBinarizeRegion_original(
    uint32_t*       __restrict__ dst_packed,
    size_t          dst_bit_offset,
    const int*      __restrict__ src,
    size_t          src_offset,
    size_t          n,
    cudaStream_t    stream)
{
    if (n == 0) {
        return cudaSuccess;
    }
    if (dst_packed == nullptr || src == nullptr) {
        return cudaErrorInvalidValue;
    }

    const size_t first_word     = dst_bit_offset / 32;
    const size_t last_word_excl = (dst_bit_offset + n + 31) / 32;
    const size_t num_words      = last_word_excl - first_word;

    constexpr int block_size = 256;
    negateAndBinarizeKernel_original<<<calcGrid(num_words, block_size),
                              block_size, 0, stream>>>(
        src, src_offset, dst_packed, dst_bit_offset, n);

    return cudaGetLastError();
}