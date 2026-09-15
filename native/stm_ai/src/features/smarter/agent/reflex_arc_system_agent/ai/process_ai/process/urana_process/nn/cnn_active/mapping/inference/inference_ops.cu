#include "inference_ops.h"
#include <cstdio>
#include <cstdlib>
#include <stdint.h>

constexpr int BLOCK = 256;

// B 段（行为位）长度与块宽：与 effect 侧 PolarLayout.TOTAL_BITS / GROUP_BITS 一致。
// 这是定理1(3) 的活动域契约——B 段必须留在输出尾部 offset = sizeA1 - 256、len 256，
// 一旦改动此约定，按定理1(3) 该设计不合法（方案 §5 裁决3）。
constexpr int B_LEN = 256;
constexpr int B_GROUP_BITS = 16;

// 中性激活值（I5，L0.1）：判决判据是 y >= 0（CnnActiveOptions.THRESHOLD），故"中性"必须取**负值**
// 才落 0 位；取 −1 即 D2 值域的下界，等价于 tanh(z→−∞) 的极限——"完全未激活"。
// 与 PolarLayout 预留段"约定 0 = 冻结"的语义一致：0 位 ⟹ MuscleGroup.decodeActivation = 0
// ⟹ TensionIntegrator 按 (1−α) 衰减 ⟹ 全部肌群静止。这是**声明的**安全中性态（不是副作用）。
constexpr float C_NEUTRAL_ACTIVATION = -1.0f;

// 奇激活（D2 参数化，方案 §7.2）：
//   activationId == 1 → tanh（默认）：1 − 2/(e^{2z}+1)
//   activationId == 2 → clipped-linear：clamp(z, −1, 1)
// 两者共用判决阈值 θ = 0（tanh(0)=0、clip(0)=0）⟹ θ 是结构常量，不暴露、不可独立组合（推论2）。
// 用 __expf 与原生 cnn_sigmoid 同族（fast math；CNN 训练可接受）。**有限的** z→+∞ 时 __expf 溢出到 inf，
// 2/inf = 0 ⟹ 结果 1；z→−∞ 时 __expf→0 ⟹ 结果 −1。
// ⚠ 上句只对**有限的大 z** 成立（L0.4 实证：z=+1e30 ⟹ y=+1）。**z = ±inf 不算"有限的大 z"**：
//   `isfinite(±inf)` 亦为 false，故 ±inf 与 NaN 一样会走下面的中性分支 ⟹ y = −1。
//   即：本函数对"非有限"统一判中性，不区分 NaN 与 inf（二者同为数值病态的信号）。
//
// I5（L0.1 有限性不变式）：上式只对**有限** z 成立。对 z = NaN（权重一旦 NaN 后必然出现）
// __expf(NaN) 仍是 NaN ⟹ y 也是 NaN ⟹ 经 prevB 逐拍拷贝且无衰减/复位，NaN 永久自持
// （诊断报告 §5）。故此处把"数值应当有限"这个不实在的要求，用实在的算子固化（设计原则5）：
// **非有限 ⟹ 中性**（含 NaN 与 ±inf）。由此 y 由构造保证有限 ⟹ prevB 有界且不再传播 NaN，
// NaN 自持通道被掐断。
// 注意：D2 的两项在 NaN 传播性上**并不等价**——tanh 传播 NaN，clipped-linear 因 fmaxf/fminf
// "返回非 NaN 操作数"而意外吞掉 NaN；故守卫对两项统一前置，不依赖激活选择（二者终点都是全 0 位）。
// sigmoid 分支刻意不写：它非法（值域不匹配有符号的 C 段 target），写进来就是让非法组合存在于代码里。
__device__ __forceinline__ float cnn_active_activate(float z, int activationId) {
    if (!isfinite(z)) {
        return C_NEUTRAL_ACTIVATION;
    }
    if (activationId == 2) {
        return fminf(1.0f, fmaxf(-1.0f, z));
    }
    return 1.0f - 2.0f / (__expf(2.0f * z) + 1.0f);
}

// 邻域取值（D4，方案 §1.3）：
//   B 段（j >= bOffset）：16 位块内模环，源为该位上一拍的行为浮点值 prevB。
//        blk = k >> 4、off = k & 15（16 是 2 的幂 ⟹ 纯位运算）；
//        km = blk<<4 | (off+15)&15、kp = blk<<4 | (off+1)&15。
//        块内相邻位属同一肌群的冗余位（GROUP_BITS=16，多数表决容错）⟹ 有共同语义（真）；
//        不跨块 ⟹ 不引入无语义耦合；16 个块一视同仁（不给预留段特判）⟹ 无分支（善）。
//   C/F 段（j < bOffset）：保持原 CNN 的空间邻接 x[j±1]，越界补 0，语义正确。
// 承载：源是浮点值而非 bit——CNN 家族载体是浮点，与载体保持一致（真，方案 §1.3）。
__device__ __forceinline__ void cnn_active_neighbors(
    const float* __restrict__ x, const float* __restrict__ prevB,
    int sizeA0, int bOffset, int j /* -> */, float& xl, float& xr) {
    if (j >= bOffset) {
        int k = j - bOffset;
        int blk = k >> 4;                 // B_GROUP_BITS == 16
        int off = k & 15;
        int km = (blk << 4) | ((off + 15) & 15);
        int kp = (blk << 4) | ((off + 1) & 15);
        xl = __ldg(&prevB[km]);
        xr = __ldg(&prevB[kp]);
    } else {
        xl = (j - 1 >= 0 && j - 1 < sizeA0) ? __ldg(&x[j - 1]) : 0.0f;
        xr = (j + 1 < sizeA0) ? __ldg(&x[j + 1]) : 0.0f;
    }
}

// push kernel：照抄 cnn_push_kernel（逐行同构）。
// 位置投影与输入侧无关——激活函数与 B 段递归都不改变 push 结构，故此处零改动。
__global__ void __launch_bounds__(BLOCK, 2)
cnn_active_push_kernel(const float* __restrict__ x, const float* __restrict__ q, const int* __restrict__ idx0, const int* __restrict__ idx1, const float* __restrict__ w0, const float* __restrict__ w1, int sizeA0 /* -> */, float* __restrict__ z) {
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

// pull + activate kernel：照抄 cnn_pull_lr_activate_kernel，两处改（见头文件差异说明）。
//   StoreTrace=true : z=trace_z 累加（保留），y=trace_y=激活(z)
//   StoreTrace=false: z=y=output（同一 buffer），激活覆盖 z（不保留累加结果）
template <bool StoreTrace>
__global__ void __launch_bounds__(BLOCK, 2)
cnn_active_pull_activate_kernel(
    const float* __restrict__ x, const float* __restrict__ l, const float* __restrict__ r, const float* __restrict__ b,
    const float* __restrict__ prevB, int activationId,
    int sizeA0, int sizeA1 /* -> */, float* z, float* y) {
    int j = blockIdx.x * blockDim.x + threadIdx.x;
    if (j >= sizeA1) return;

    int bOffset = sizeA1 - B_LEN;

    float zj = z[j];
    float xl, xr;
    cnn_active_neighbors(x, prevB, sizeA0, bOffset, j /* -> */, xl, xr);
    zj += __ldg(&l[j]) * xl;
    zj += __ldg(&r[j]) * xr;
    zj += __ldg(&b[j]);

    float yj = cnn_active_activate(zj, activationId);

    if constexpr (StoreTrace) {
        z[j] = zj;
        y[j] = yj;
    } else {
        // NoTrace: z==y 同一 buffer，激活值直接覆盖
        y[j] = yj;
    }
}

// refresh_cache kernel：照抄 cnn_refresh_cache_kernel（逐行同构）。
// j0=floor(p), j1=j0+1; idx_k=j_k if (0<=j_k<sizeA1 且 |p-j_k|<1) else -1; w_k=1-(p-j_k)^2。
__global__ void __launch_bounds__(BLOCK, 2)
cnn_active_refresh_cache_kernel(const float* __restrict__ p, int sizeA0, int sizeA1 /* -> */, int* __restrict__ idx0, int* __restrict__ idx1, float* __restrict__ w0, float* __restrict__ w1) {
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

extern "C" void cnn_active_forward_layer_trace(
    const float* x, const float* q, const float* l, const float* r, const float* b,
    const int* idx0, const int* idx1, const float* w0, const float* w1,
    const float* prevB, int activationId,
    int sizeA0, int sizeA1, cudaStream_t stream /* -> */,
    float* trace_z, float* trace_y) {

    if (trace_z == nullptr || trace_y == nullptr) {
        fprintf(stderr, "ERROR: trace_z/trace_y is null in cnn_active_forward_layer_trace\n");
        abort();
    }
    if (prevB == nullptr) {
        fprintf(stderr, "ERROR: prevB is null in cnn_active_forward_layer_trace\n");
        abort();
    }

    cudaMemsetAsync(trace_z, 0, sizeA1 * sizeof(float), stream);

    dim3 block(BLOCK);
    dim3 grid_push((unsigned int)((sizeA0 + BLOCK - 1) / BLOCK));
    dim3 grid_pull((unsigned int)((sizeA1 + BLOCK - 1) / BLOCK));

    cnn_active_push_kernel<<<grid_push, block, 0, stream>>>(x, q, idx0, idx1, w0, w1, sizeA0 /* -> */, trace_z);
    cnn_active_pull_activate_kernel<true><<<grid_pull, block, 0, stream>>>(x, l, r, b, prevB, activationId, sizeA0, sizeA1 /* -> */, trace_z, trace_y);
}

extern "C" void cnn_active_forward_layer_notrace(
    const float* x, const float* q, const float* l, const float* r, const float* b,
    const int* idx0, const int* idx1, const float* w0, const float* w1,
    const float* prevB, int activationId,
    int sizeA0, int sizeA1, cudaStream_t stream /* -> */,
    float* y) {

    if (prevB == nullptr) {
        fprintf(stderr, "ERROR: prevB is null in cnn_active_forward_layer_notrace\n");
        abort();
    }

    cudaMemsetAsync(y, 0, sizeA1 * sizeof(float), stream);

    dim3 block(BLOCK);
    dim3 grid_push((unsigned int)((sizeA0 + BLOCK - 1) / BLOCK));
    dim3 grid_pull((unsigned int)((sizeA1 + BLOCK - 1) / BLOCK));

    cnn_active_push_kernel<<<grid_push, block, 0, stream>>>(x, q, idx0, idx1, w0, w1, sizeA0 /* -> */, y);
    cnn_active_pull_activate_kernel<false><<<grid_pull, block, 0, stream>>>(x, l, r, b, prevB, activationId, sizeA0, sizeA1 /* -> */, y, y);
}

extern "C" void cnn_active_refresh_cache(
    const float* p, int sizeA0, int sizeA1, cudaStream_t stream /* -> */,
    int* idx0, int* idx1, float* w0, float* w1) {

    dim3 block(BLOCK);
    dim3 grid((unsigned int)((sizeA0 + BLOCK - 1) / BLOCK));

    cnn_active_refresh_cache_kernel<<<grid, block, 0, stream>>>(p, sizeA0, sizeA1 /* -> */, idx0, idx1, w0, w1);
}
