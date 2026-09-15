#include "init_ops.h"
#include <cstdio>
#include <cstdlib>
#include <stdint.h>

constexpr int BLOCK = 256;

// PCG hash：与 core/containers/vector/VectorMapping.cu 的 pcgHash 同源（同一算法，镜像实现）。
// 复制而非共享：本组 kernel 自包含（定理1(2)：A' 不与原 CNN 共享可变演化路径），
// 与"照抄 mechanism"的划分规则一致（方案 §零）。
__device__ __forceinline__ uint32_t cnn_active_pcg_hash(uint32_t state) {
    state = state * 747796405u + 2891336453u;
    uint32_t word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

// 对称零均值填充：u01 ∈ [0,1) → (2·u01 − 1) ∈ [−1,1) → ×bound ⟹ [−bound, +bound)。
// 与 fillRandomFloatsKernel 逐行同构，只改 (u01 * 2 - 1) 这一处（照抄 + 一处改）。
// 注意 bound<=0 时填 0 的退化分支与核心一致（构造期 fail-fast 由 Java 侧 CnnActiveOptions 负责）。
__global__ void __launch_bounds__(BLOCK, 2)
cnn_active_fill_symmetric_kernel(float* __restrict__ data, int n, float bound, uint32_t seed) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= n) return;
    uint32_t h = cnn_active_pcg_hash(seed ^ (uint32_t)i);
    float u01 = (float)h * (1.0f / 4294967296.0f);  // [0,1)
    data[i] = (bound > 0.0f) ? (u01 * 2.0f - 1.0f) * bound : 0.0f;
}

extern "C" void cnn_active_fill_symmetric(
    int n, float bound, uint64_t seed, cudaStream_t stream /* -> */, float* data) {

    if (n <= 0) return;
    if (data == nullptr) {
        fprintf(stderr, "ERROR: data is null in cnn_active_fill_symmetric\n");
        abort();
    }

    uint32_t s = (uint32_t)seed ^ (uint32_t)(seed >> 32);
    dim3 block(BLOCK);
    dim3 grid((unsigned int)((n + BLOCK - 1) / BLOCK));

    cnn_active_fill_symmetric_kernel<<<grid, block, 0, stream>>>(data, n, bound, s);
}
