#include "gradient_ops.h"
#include <cstdio>


__global__ void gradientSignFlipKernel_original(
    int *__restrict__ dz,
    const int *__restrict__ da,
    const uint32_t *__restrict__ fz_packed,
    const uint32_t *__restrict__ b_packed,
    size_t n)
{
    // 网格跨步循环：每个线程处理多个元素，提升ILP
    const size_t stride = blockDim.x * gridDim.x;
    for (size_t i = blockIdx.x * blockDim.x + threadIdx.x; i < n; i += stride)
    {
        // 1. 位包索引与偏移计算
        const size_t pack_idx = i >> 5;
        const int bit_offset = i & 31;

        // 2. 只读缓存加载位压缩数据
        const uint32_t fz_word = __ldg(&fz_packed[pack_idx]);
        const uint32_t b_word = __ldg(&b_packed[pack_idx]);

        // 3. 批量计算32位翻转掩码
        const uint32_t flip_mask_word = (~fz_word) & b_word;

        // 4. 核心优化：直接移位生成全0/全1掩码，省去按位与
        int mask = (int)(flip_mask_word << (31 - bit_offset)) >> 31;

        // 5. 只读加载梯度，补码位运算实现条件取反
        const int da_val = __ldg(&da[i]);
        dz[i] = (da_val ^ mask) - mask;
    }
}

/**
 * @brief 梯度符号翻转算子主机端调用入口
 * @param[out] dz        输出梯度，设备端int指针，长度为n
 * @param[in]  da        输入梯度，设备端const int指针，长度为n
 * @param[in]  fz_packed 位压缩的前向符号掩码，设备端const uint32_t指针
 *                       有效长度至少为 ceil(n / 32) 个uint32_t
 * @param[in]  b_packed  位压缩的反向符号掩码，设备端const uint32_t指针
 *                       有效长度至少为 ceil(n / 32) 个uint32_t
 * @param[in]  n         梯度元素总数
 * @param[in]  stream    CUDA流，用于异步执行，默认为空流
 * @return cudaError_t   核函数启动阶段的错误码，执行期错误需同步后检查
 */
cudaError_t gradientSignFlip_original(
    int *__restrict__ dz,
    const int *__restrict__ da,
    const uint32_t *__restrict__ fz_packed,
    const uint32_t *__restrict__ b_packed,
    size_t n,
    cudaStream_t stream)
{
    // 1. 边界与参数合法性检查
    if (n == 0)
    {
        return cudaSuccess;
    }
    if (dz == nullptr || da == nullptr ||
        fz_packed == nullptr || b_packed == nullptr)
    {
        return cudaErrorInvalidValue;
    }

    // 2. 线程配置：256线程/块为通用最优解
    // 为32的整数倍，匹配warp大小，兼顾占用率与指令级并行(ILP)
    constexpr int block_size = 256;
    const dim3 block_dim(block_size);
    // 向上取整计算网格大小，配合核内网格跨步循环覆盖任意n
    const dim3 grid_dim(static_cast<size_t>(n + block_size - 1) / block_size);

    // 3. 启动核函数，绑定指定CUDA流
    gradientSignFlipKernel_original<<<grid_dim, block_dim, 0, stream>>>(
        dz, da, fz_packed, b_packed, n);

    // 4. 返回核函数启动阶段的错误（如配置非法、参数不匹配等）
    return cudaGetLastError();
}

// --- Backward Activation ---

__device__ __forceinline__ int32_t get_bit(const uint32_t *__restrict__ bit_array, int index)
{
    return static_cast<int32_t>((bit_array[index >> 5] >> (index & 31)) & 1U);
}

template <int ELEMS_PER_THREAD = 8>
__global__ void backward_activation_final_kernel_original(
    const int32_t *__restrict__ dz,
    const int32_t *__restrict__ p,
    const uint32_t *__restrict__ q,
    const uint32_t *__restrict__ l,
    const uint32_t *__restrict__ r,
    int32_t *__restrict__ da,
    int batch_size,
    int n_curr,
    int n_prev)
{
    const int tid = blockIdx.x * blockDim.x + threadIdx.x;
    const int total_threads = gridDim.x * blockDim.x;
    const int stride = total_threads * ELEMS_PER_THREAD;
    const int total_out = batch_size * n_prev;
    const int dz_row_stride = n_curr;

    for (int base = tid * ELEMS_PER_THREAD; base < total_out; base += stride)
    {
        const int batch_idx = base / n_prev;
        const int i_start = base % n_prev;
        const int32_t *dz_row = dz + batch_idx * dz_row_stride;

#pragma unroll
        for (int k = 0; k < ELEMS_PER_THREAD; ++k)
        {
            const int i = i_start + k;
            if (i >= n_prev)
                break;

            const int32_t qi = get_bit(q, i);
            const int32_t pi = __ldg(&p[i]);

            // 防御性夹取：p[i] 理论上应在 [0, n_curr) 内，但若网络未初始化或
            // 数据损坏导致 pi 越界，直接访问 dz_row[pi] 会触发 illegal memory access，
            // 使整个 CUDA 上下文失效（后续所有 op 都会误报同一 sticky 错误）。
            // 这里夹取后仅用于内存访问；下面的 pi==ip1/pi==im1 比较仍用原始 pi。
            const int safe_pi = min(max(pi, 0), n_curr - 1);
            const int32_t term1 = qi * __ldg(&dz_row[safe_pi]);

            const int ip1 = i + 1;
            const int32_t valid_p1 = (ip1 >= 0 && ip1 < n_curr) ? 1 : 0;
            const int safe_ip1 = min(max(ip1, 0), n_curr - 1);
            const int32_t li = get_bit(l, safe_ip1);
            const int32_t val_p1 = __ldg(&dz_row[safe_ip1]);
            const int32_t mask2 = (1 - (qi & (pi == ip1))) & valid_p1 & li;
            const int32_t term2 = mask2 * val_p1;

            const int im1 = i - 1;
            const int32_t valid_m1 = (im1 >= 0 && im1 < n_curr) ? 1 : 0;
            const int safe_im1 = min(max(im1, 0), n_curr - 1);
            const int32_t ri = get_bit(r, safe_im1);
            const int32_t val_m1 = __ldg(&dz_row[safe_im1]);
            const int32_t mask3 = (1 - (qi & (pi == im1))) & valid_m1 & ri;
            const int32_t term3 = mask3 * val_m1;

            da[base + k] = term1 + term2 + term3;
        }
    }
}

/**
 * @brief 激活反向传播核函数的主机调用入口
 * @tparam ELEMS_PER_THREAD 每个线程处理的输出元素数，与核函数保持一致，默认8
 * @param dz    设备指针，上层回传的梯度，形状 [batch_size, n_curr]，行优先存储
 * @param p     设备指针，前层到当前层的索引映射，长度 n_prev
 * @param q     设备指针，直接连接标记比特数组，长度 n_prev
 * @param l     设备指针，左连接标记比特数组，长度 n_curr
 * @param r     设备指针，右连接标记比特数组，长度 n_curr
 * @param da    设备指针，输出的前层梯度，形状 [batch_size, n_prev]，行优先存储
 * @param batch_size 批大小
 * @param n_curr 当前层神经元数量
 * @param n_prev 前层神经元数量
 * @param stream CUDA流，用于异步执行，默认使用默认流
 */
__host__ void backwardActivation_original(
    const int32_t *__restrict__ dz,
    const int32_t *__restrict__ p,
    const uint32_t *__restrict__ q,
    const uint32_t *__restrict__ l,
    const uint32_t *__restrict__ r,
    int32_t *__restrict__ da,
    int batch_size,
    int n_curr,
    int n_prev,
    cudaStream_t stream)
{
    // 非法输入直接返回，避免无效核函数启动
    if (batch_size <= 0 || n_prev <= 0 || n_curr <= 0)
    {
        return;
    }

    // 线程块大小：256是CUDA全架构通用最优值（8个warp，保证warp对齐与SM资源利用率）
    constexpr int block_size = 256;
    // 总输出元素数量 = 批大小 × 前层神经元数
    const int total_out = batch_size * n_prev;

    // 计算所需总线程数：每个线程处理 ELEMS_PER_THREAD 个元素，向上取整
    constexpr int ELEMS_PER_THREAD = 8;
    const int total_threads_needed = (total_out + ELEMS_PER_THREAD - 1) / ELEMS_PER_THREAD;
    // 计算网格大小：向上取整保证覆盖所有线程
    const int grid_size = (total_threads_needed + block_size - 1) / block_size;

    // 启动模板核函数
    // <<<网格大小, 线程块大小, 共享内存大小, CUDA流>>>
    backward_activation_final_kernel_original<ELEMS_PER_THREAD>
        <<<grid_size, block_size, 0, stream>>>(
            dz, p, q, l, r, da,
            batch_size, n_curr, n_prev);

    // 检查核函数启动阶段的错误（配置错误、参数非法、资源不足等）
    cudaError_t launch_err = cudaGetLastError();
    if (launch_err != cudaSuccess)
    {
        fprintf(stderr, "[backwardLayer_original] backwardActivation_original failed: %s\n",
                cudaGetErrorString(launch_err));
    }
}

cudaError_t backwardLayer_original(
    int* da0,
    const int* da1,
    const uint32_t* fz_packed,
    const uint32_t* b_packed,
    const int* p,
    const uint32_t* q_packed,
    const uint32_t* l_packed,
    const uint32_t* r_packed,
    int* dz1_workspace,
    int batch_size,
    int n_curr,
    int n_prev,
    cudaStream_t stream
)
{
    // dz1 由外部预分配传入（调用方管理生命周期），3 个串行 GradCell 共享一份。
    // 设计原则（真善美）：用预分配的实在缓冲区替代每次 cudaMallocAsync 的瞬时分配，
    // 消除 ~796 MB 内存池残留，避免内存碎片。
    int* dz1 = dz1_workspace;

    // 1. 计算 dz1（gradientSignFlip_original 会覆盖 dz1 的全部内容，无需清零）
    cudaError_t err = gradientSignFlip_original(dz1, da1,
                         fz_packed,
                         b_packed,
                         static_cast<size_t>(n_curr) * batch_size, stream);
    if (err != cudaSuccess) {
        fprintf(stderr, "[backwardLayer_original] gradientSignFlip_original failed: %s\n", cudaGetErrorString(err));
        return err;
    }

    // 2. 计算 da0
    backwardActivation_original(dz1,
                       p,
                       q_packed,
                       l_packed,
                       r_packed,
                       da0,
                       batch_size, n_curr, n_prev, stream);

    // 检查 backwardActivation_original 内部的异步错误
    err = cudaGetLastError();
    if (err != cudaSuccess) {
        fprintf(stderr, "[backwardLayer_original] backwardActivation_original failed: %s\n", cudaGetErrorString(err));
        return err;
    }

    // 不再 cudaFreeAsync：dz1 由外部管理，3 个串行 GradCell 共享复用。
    // 不再 cudaStreamSynchronize：Windows WDDM 下每次同步开销 5-20ms，
    // 执行期错误由 tick 末尾 copyToHost 的隐式同步捕获。
    return cudaSuccess;
}

// --- In-place Gradient Descent ---

namespace detail {

__device__ __forceinline__ int get_bit_val(const uint32_t *__restrict__ data, int index)
{
    return (data[index >> 5] >> (index & 31)) & 1;
}

__device__ __forceinline__ void set_bit_val(uint32_t *__restrict__ data, int index, int val)
{
    uint32_t mask = 1U << (index & 31);
    if (val)
        atomicOr(&data[index >> 5], mask);
    else
        atomicAnd(&data[index >> 5], ~mask);
}

// ============================================================================
// 并发安全不变量（方案 C：快慢环共享权重并发读写）
// ============================================================================
// UranaSystem 拆为快环（行动者推理 forward，只读权重）与慢环（梯度下降，写权重），
// 两环跑在各自 CUDA 流上并发。本 kernel 是慢环写权重的唯一路径，与快环 forward
// 的权重读并发执行。安全性由以下两条硬件级不变量保证（真善美第 3 条：把"并发
// 安全"这个不实在的约束用实在的、文档化的硬件不变量固化）：
//
//   1. b/q/l/r（bool 权重，packed uint32）：经 set_bit_val 写入，其内部用
//      atomicOr / atomicAnd 对所在 32 位 word 做原子 RMW。快环 forward 以对齐
//      32 位读这些 word，CUDA 保证对齐 32 位 load/store 原子 → 快环读到 old 或
//      new word，绝不撕裂。
//
//   2. p（int32 排列索引）：本 kernel 以 p[i] = new_p 写入（对齐 32 位 store，
//      单字原子）。关键：new_p 被 clamp 到 [0, n_curr-1]（见下方赋值），故快环
//      forward 读到的 old_p 或 new_p 均为有效索引，bnn_push_p 用其做索引访问
//      不会越界、不会崩。
//
// 结论：快环 forward 在慢环梯度下降的更新窗口内会读到"部分更新"的权重（某些
// bit/index 已更新、某些未更新），等价于轻微噪声；对 BNN 反应控制可接受，下一
// 轮即恢复一致。无需双缓冲、无需跨流同步、零额外显存。
// ============================================================================
template <int ELEMS_PER_THREAD>
__global__ void backward_activation_and_gradient_descent_kernel_original(
    const int32_t *__restrict__ dz,
    const uint32_t *__restrict__ a_prev,
    int32_t *__restrict__ p,
    uint32_t *__restrict__ q_packed,
    uint32_t *__restrict__ l_packed,
    uint32_t *__restrict__ r_packed,
    uint32_t *__restrict__ b_packed,
    int32_t *__restrict__ da,
    int n_curr,
    int n_prev)
{
    const int tid = blockIdx.x * blockDim.x + threadIdx.x;
    const int total_threads = gridDim.x * blockDim.x;
    const int stride = total_threads * ELEMS_PER_THREAD;
    const int total = max(n_curr, n_prev);

    for (int base = tid * ELEMS_PER_THREAD; base < total; base += stride)
    {
#pragma unroll
        for (int k = 0; k < ELEMS_PER_THREAD; ++k)
        {
            const int idx = base + k;
            if (idx >= total)
                break;

            if (idx < n_curr)
            {
                int b_val = get_bit_val(b_packed, idx);
                int new_b = b_val + dz[idx];
                if (new_b < 0) new_b = 0;
                else if (new_b > 1) new_b = 1;
                set_bit_val(b_packed, idx, new_b);
            }

            if (idx < n_prev)
            {
                const int i = idx;
                const int32_t ai = get_bit_val(a_prev, i);
                const int32_t pi = p[i];
                const int32_t qi = get_bit_val(q_packed, i);
                const int safe_pi = min(max(pi, 0), n_curr - 1);
                const int32_t dz_pi = dz[safe_pi];

                // da: 三项加法累加（公式1），与 backward_activation_final_kernel_original 同构。
                //   互斥条件 NOT(q·δ): 直连 q=1 且 p 指向旁路目标时, 该旁路不算(避免梯度重复)。
                //   旧实现把加法退化成 if/else 选择, q=1 时漏算旁路, 且把 dl/dr 的无条件
                //   更新也误塞进 else 分支 —— 违背"加法"语义(公式1 的 + 是数值加法)。
                const int ip1 = i + 1;
                const int im1 = i - 1;
                const int32_t valid_p1 = (ip1 >= 0 && ip1 < n_curr) ? 1 : 0;
                const int32_t valid_m1 = (im1 >= 0 && im1 < n_curr) ? 1 : 0;
                // 必须夹取: 当 n_prev > n_curr 且 i 接近 n_prev 时, ip1/im1 可能 >= n_curr,
                // 直接索引 dz[ip1] / get_bit_val(l_packed, ip1) 会越界触发 illegal memory access。
                const int safe_ip1 = min(max(ip1, 0), n_curr - 1);
                const int safe_im1 = min(max(im1, 0), n_curr - 1);

                const int32_t term1 = qi * dz_pi;

                const int32_t li = get_bit_val(l_packed, safe_ip1);
                const int32_t mask2 = (1 - (qi & (pi == ip1))) & valid_p1 & li;
                const int32_t dz_ip1 = dz[safe_ip1];
                const int32_t term2 = mask2 * dz_ip1;

                const int32_t ri = get_bit_val(r_packed, safe_im1);
                const int32_t mask3 = (1 - (qi & (pi == im1))) & valid_m1 & ri;
                const int32_t dz_im1 = dz[safe_im1];
                const int32_t term3 = mask3 * dz_im1;

                da[i] = term1 + term2 + term3;

                // l 更新（公式4: dl_{i+1} = dz_{i+1} · a_i, 无条件, 仅边界保护）
                if (valid_p1)
                {
                    int new_l = li - dz_ip1 * ai;
                    if (new_l < 0) new_l = 0; else if (new_l > 1) new_l = 1;
                    set_bit_val(l_packed, safe_ip1, new_l);
                }
                // r 更新（dr_{i-1} = dz_{i-1} · a_i, 无条件, 仅边界保护）
                if (valid_m1)
                {
                    int new_r = ri - dz_im1 * ai;
                    if (new_r < 0) new_r = 0; else if (new_r > 1) new_r = 1;
                    set_bit_val(r_packed, safe_im1, new_r);
                }

                // q, p 更新（公式吻合, 保持不动）
                int new_q = qi - dz_pi * ai;
                if (new_q < 0) new_q = 0; else if (new_q > 1) new_q = 1;
                set_bit_val(q_packed, i, new_q);
                int new_p = pi + dz_pi * ai * qi;
                if (new_p < 0) new_p = 0; else if (new_p >= n_curr) new_p = n_curr - 1;
                p[i] = new_p;
            }
        }
    }
}

} // namespace detail

__host__ cudaError_t backwardActivationAndGradientDescent_original(
    int32_t *__restrict__ dz,
    const uint32_t *__restrict__ a_prev,
    int32_t *__restrict__ p,
    uint32_t *__restrict__ q_packed,
    uint32_t *__restrict__ l_packed,
    uint32_t *__restrict__ r_packed,
    uint32_t *__restrict__ b_packed,
    int32_t *__restrict__ da,
    int n_curr,
    int n_prev,
    cudaStream_t stream)
{
    constexpr int ELEMS_PER_THREAD = 8;
    constexpr int block_size = 256;
    const int total = std::max(n_curr, n_prev);
    const int total_threads_needed = (total + ELEMS_PER_THREAD - 1) / ELEMS_PER_THREAD;
    const int grid_size = (total_threads_needed + block_size - 1) / block_size;

    detail::backward_activation_and_gradient_descent_kernel_original<ELEMS_PER_THREAD>
        <<<grid_size, block_size, 0, stream>>>(
            dz, a_prev, p, q_packed, l_packed, r_packed, b_packed, da,
            n_curr, n_prev);

    cudaError_t err = cudaGetLastError();
    if (err != cudaSuccess) {
        fprintf(stderr, "[GD-Activation] Kernel launch failed: %s\n", cudaGetErrorString(err));
    }
    return err;
}

__host__ cudaError_t backwardGradientDescentLayer_original(
    int32_t *__restrict__ da0,
    const int32_t *__restrict__ da1,
    const uint32_t *__restrict__ a_prev,
    const uint32_t *__restrict__ fz_packed,
    uint32_t *__restrict__ b_packed,
    int32_t *__restrict__ p,
    uint32_t *__restrict__ q_packed,
    uint32_t *__restrict__ l_packed,
    uint32_t *__restrict__ r_packed,
    int32_t *__restrict__ dz_workspace,
    int n_curr,
    int n_prev,
    cudaStream_t stream)
{
    if (n_curr <= 0 || n_prev <= 0) return cudaSuccess;

    // dz 由外部预分配传入（调用方管理生命周期），3 个串行 GradCell 共享一份。
    int32_t *dz = dz_workspace;

    cudaError_t err = gradientSignFlip_original(dz, da1, fz_packed, b_packed, static_cast<size_t>(n_curr), stream);
    if (err != cudaSuccess) {
        fprintf(stderr, "[GDLayer] gradientSignFlip_original failed: %s\n", cudaGetErrorString(err));
        return err;
    }

    err = backwardActivationAndGradientDescent_original(
        dz, a_prev, p, q_packed, l_packed, r_packed, b_packed, da0,
        n_curr, n_prev, stream
    );

    // 不再 cudaFreeAsync：dz 由外部管理，3 个串行 GradCell 共享复用。
    // 不再 cudaStreamSynchronize：Windows WDDM 下每次同步开销 5-20ms，
    // 执行期错误由 tick 末尾 copyToHost 的隐式同步捕获。
    return err;
}
