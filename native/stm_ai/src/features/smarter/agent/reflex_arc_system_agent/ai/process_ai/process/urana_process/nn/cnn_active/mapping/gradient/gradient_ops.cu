#include "gradient_ops.h"
#include <cstdio>
#include <cstdlib>
#include <stdint.h>

constexpr int BLOCK = 256;

// B 段长度：与 effect 侧 PolarLayout.TOTAL_BITS 一致（定理1(3) 活动域契约）。
constexpr int B_LEN = 256;

// C 域投影（I2，L0.1）——本模块唯一的"有界化"不变量，也是消除发散的**最小充分**约束。
//
// 【是什么】buf_tC / tC 是流程的 **C2**（GradCellOp 注释："bw 以 buf_tC 为出参（S1=C2 落位）"），
//   即下一轮的**继承信息**。C 的合法值域就是输出 C 段的值域 [−1,+1]：
//   C 段来自 extractC(y)，而 y = 奇激活 ∈ (−1,1)（D2 值域）。同理 x 的 C 段也由它灌入（assembleX）。
//
// 【为什么在这里】本 kernel 是 C2 的**唯一产出点**：GradCellOp 以同一 tC 同时注入入参+出参（别名），
//   阶段二传 bufTc = null（<false> 不写）。故在此投影**一次**，即同时约束了两个下游消费者：
//   正向的 C 种子（assembleX）与目标 T.C（assembleT）。单一数据源（真善美第3条）。
//
// 【为什么是"投影"而非"篡改梯度本义"】C2 的**语义角色**是 C（继承信息），它会被写进输入向量的 C 段，
//   必须落在 C 的合法域内。把学习规则的产出投影回合法域 = 投影式学习（projected learning），
//   与 BNN 侧既有的契约「C 在输入层梯度中的区间 = [0, cLen)」互补——那边定的是**区间**，这边定的是**值域**。
//
// 【没有它会怎样】Ext(X) 的 C 段无界 ⟹ T.C := C2 无界 ⟹ δ = 2(y−T)a′ 无界 ⟹ Δw = lr·δ·x邻域 无界
//   ⟹ 自指环（∂x ↔ T）几何增长 ⟹ float32 溢出 ⟹ NaN（诊断报告 §4）。有它则 |T| ≤ 1
//   ⟹ |δ| ≤ 4 ⟹ |Δw| ≤ 0.04 ⟹ 权重至多**线性**增长，不可能溢出（方案 §2 最小性论证）。
//
// 【为什么用 fminf/fmaxf 而不是条件表达式】fmaxf/fminf 在其中一个操作数为 NaN 时返回**另一个**操作数，
//   故 NaN 被投影到下界 −1（而不是继续传播）——同一处顺带完成 NaN 清理，与 I5 同向。
constexpr float C_DOMAIN_LIMIT = 1.0f;

__device__ __forceinline__ float cnn_active_project_c_domain(float v) {
    return fminf(C_DOMAIN_LIMIT, fmaxf(-C_DOMAIN_LIMIT, v));
}

// ──────────────────────────── L0.2 可用性不变量 ────────────────────────────
//
// I3（权重限幅）与 I4（B 段自环收缩）：把"运行期权重不该离初始化量级太远"这条**不实在**的要求，
// 固化成**实在**的算子（设计原则5）。它们是 I2 之后的第二级（方案 §2 两级表：I2 解决"不崩"，
// I3/I4 追加解决"不提前退化成原 CNN 的饱和墙"）。
//
// 【为什么是结构常量而非滑块】（方案 §5.1 裁决）：若把限幅值/收缩比做成 Ext(D) 的可选参数，
// 按推论2 必须把含发散组合的取值裁掉；裁完等价于"不可选"，却白白多出一个滑块（公理(3)：熵增）。
// 故与 CnnActiveOptions.THRESHOLD 同构，**退化为结构常量**：不暴露、不设第二读路径。
//   两个推论（务必遵守）：
//   (a) 不给 cnn_active_backward_layer 加参数 —— 保持签名不变，零 bridge / 零 Java 侧改动；
//   (b) **不在 Java 侧留运行期数值副本** —— 同一常量存在两个数值副本＝可静默漂移，
//       违反真善美第1条"真"与第3条"单一数据源"。Java 侧只**文档化**（CnnActiveOptions 的 L0.2 javadoc），
//       数值的权威定义只此一处。L0.3 若诊断面板需显示该界限，应另加 native 取值函数，而非复制常量。
//
// WEIGHT_LIMIT：q/l/r/b 的幅值上限。取 4.0 = CnnActiveMapperFactory.MAX_BOUND（初始化半径上限），
//   使初始化滑块的**整个取值范围**在运行期仍可达（方案 §5.3 选项乙：滑块管初始化、不变量管可行集）。
//   其精确值属 L1 采样标定的范畴；L0 只主张"约束的形式必须存在"（方案 §5.4）。
constexpr float WEIGHT_LIMIT = 4.0f;

// LOOP_GAIN_CAP：B 段自环增益上限（|l_j|+|r_j| ≤ cap < 1）。必须严格 < 1 —— 前向 B 段递归
//   z_j ← l_j·prevB[km] + r_j·prevB[kp] + …（inference_ops.cu:106-107，且 |prevB| ≤ 1）的增益即 |l_j|+|r_j|，
//   只有 < 1 才是收缩映射。诊断报告 §3.3 实测：该增益中位数由初始 0.50 漂到 1.08→1.14（超临界）＝发散主因之一。
//   取 0.9（ε = 0.1，方案 §5.4）——低于默认初始化偶发的上界（bound=0.5 时 |l|+|r| 最坏 1.0），
//   故首步即把分布尾部的超临界样本拉回；中位数 0.50 不受影响。
constexpr float LOOP_GAIN_CAP = 0.9f;

// I3 的具体算子：非有限 ⟹ 0（该权重中性、不贡献），否则投影到 [−W, W]。
//   "非有限 ⟹ 0"与 I5 同向（I5 把非有限 δ 置 0、把非有限 z 判为中性）。之所以不直接用
//   fminf(W, fmaxf(−W, v)) 收尾——那会把 NaN 投影到 −W（一个有符号的任意值），语义上不如"不贡献"（0）自洽。
__device__ __forceinline__ float cnn_active_clamp_weight(float v) {
    if (!isfinite(v)) {
        return 0.0f;
    }
    return fminf(WEIGHT_LIMIT, fmaxf(-WEIGHT_LIMIT, v));
}

// 激活导数 a′(z)（D2，方案 §7.2）：
//   1=tanh          → 1 − y²（只用 y；渐进趋 0，软饱和，还能学）
//   2=clipped-linear → (|z| < 1) ? 1 : 0（|z| ≥ 1 处硬零 ⟹ 该单元永久不学，故仅作可选）
// sigmoid 的 y(1−y) 刻意不写：它不在 Ext(D) 内（值域不匹配有符号的 C 段 target，非法）。
__device__ __forceinline__ float cnn_active_activate_deriv(float y, float z, int activationId) {
    if (activationId == 2) {
        return (fabsf(z) < 1.0f) ? 1.0f : 0.0f;
    }
    return 1.0f - y * y;
}

// 邻域取值：与 inference_ops.cu 的 cnn_active_neighbors 同构（同一 D4 约定的两侧实现）。
// B 段（j >= bOffset）取 prevB 的 16 位块内模环；C/F 段取 x[j±1]，越界补 0。
// 正向把这个值当输入特征用，反向就把同一个值当它的梯度系数用（∂l_j = δ_j · 该值）。
__device__ __forceinline__ void cnn_active_neighbors(
    const float* __restrict__ x, const float* __restrict__ prevB,
    int sizeA0, int bOffset, int j /* -> */, float& xl, float& xr) {
    if (j >= bOffset) {
        int k = j - bOffset;
        int blk = k >> 4;                 // GROUP_BITS == 16
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

// Kernel1（sizeA1）：计算 δ 并更新 buf_l/r/b。
// δ_j = 2(y_j − target_j) · a′(z_j)
// ∂l_j = δ_j · 邻域真值左（B 段 prevB[km]）；∂r_j = δ_j · 邻域真值右（B 段 prevB[kp]）；∂b_j = δ_j
// 更新后施加 I3（l/r/b 限幅到 [−W, W]）与 I4（仅 B 段 j >= bOffset：|l_j|+|r_j| ≤ LOOP_GAIN_CAP）——
// 常量与推导见文件头 "L0.2 可用性不变量"。
__global__ void __launch_bounds__(BLOCK, 2)
cnn_active_backward_delta_kernel(
    const float* __restrict__ trace_z,
    const float* __restrict__ trace_y,
    const float* __restrict__ target,
    const float* __restrict__ x,
    const float* __restrict__ prevB,
    int activationId,
    int sizeA0, int sizeA1, float lr /* -> */,
    float* __restrict__ buf_l,
    float* __restrict__ buf_r,
    float* __restrict__ buf_b,
    float* __restrict__ dz)
{
    int j = blockIdx.x * blockDim.x + threadIdx.x;
    if (j >= sizeA1) return;

    int bOffset = sizeA1 - B_LEN;

    float yj = __ldg(&trace_y[j]);
    float zj = __ldg(&trace_z[j]);
    float tj = __ldg(&target[j]);
    float dy = yj - tj;                                        // (y - target)
    float sd = cnn_active_activate_deriv(yj, zj, activationId); // a'(z)
    float delta = 2.0f * dy * sd;                              // δ = 2(y-target)·a'(z)

    // I5（L0.1 有限性不变式）：非有限的 δ 视为 0 梯度（本步对这些参数不更新）。
    // 必要性：dy = y − t，若 target 是 NaN（例如从磁盘载入了上一轮中毒的 C2），
    // 则 NaN × 0 = NaN（IEEE）——δ 会带着 NaN 进入权重更新式并永久污染权重。
    // 置 0 后：权重保持原值（不被进一步污染），且 dx 归 0 ⟹ C2 投影后回到 0 ⟹ 下一轮 T.C 有界。
    // 这一步把"中毒"变成"该步不学"，而不是"把毒扩散出去"。
    if (!isfinite(delta)) {
        delta = 0.0f;
    }

    dz[j] = delta;

    // 邻域真值（B 段取自 prevB，其余取自 x[j±1]，越界补 0）——前向的输入特征，反向即其梯度系数
    float xl, xr;
    cnn_active_neighbors(x, prevB, sizeA0, bOffset, j /* -> */, xl, xr);

    float grad_l = delta * xl;
    float grad_r = delta * xr;
    float grad_b = delta;

    // I3（L0.2 权重限幅）：把**更新后**的 l/r/b 钳回 [−W, W]（非有限 ⟹ 0）。
    float nl = cnn_active_clamp_weight(buf_l[j] - lr * grad_l);
    float nr = cnn_active_clamp_weight(buf_r[j] - lr * grad_r);
    float nb = cnn_active_clamp_weight(buf_b[j] - lr * grad_b);

    // I4（L0.2 B 段自环收缩）：**仅 B 段**（j >= bOffset）保证 |l_j|+|r_j| ≤ LOOP_GAIN_CAP < 1。
    //   必要性：前向 B 段（j >= bOffset）的自环项为 l[j]·prevB[km] + r[j]·prevB[kp]
    //   （inference_ops.cu:106-107），且 |prevB| ≤ 1（D2 值域 + I5）⟹ 该递归增益恰为 |l_j|+|r_j|。
    //   超限则**同比例缩**——保持 l_j : r_j 的比与二者符号，即投影到约束集、方向不丢
    //   （与 I2 同属"投影式学习"，真善美第1条"真"：不篡改梯度本义，只把可行域外的解投回域内）。
    //   顺序：**先 I3 后 I4** —— I3 已去非有限 ⟹ 此处 |nl|+|nr| 必有限，可安全作除数（g > cap > 0 ⟹ 无 0 除）。
    if (j >= bOffset) {
        float g = fabsf(nl) + fabsf(nr);
        if (g > LOOP_GAIN_CAP) {
            float s = LOOP_GAIN_CAP / g;
            nl *= s;
            nr *= s;
        }
    }

    buf_l[j] = nl;
    buf_r[j] = nr;
    buf_b[j] = nb;
}

// Kernel2（sizeA0）：计算 dInput + bufTc + 更新 buf_p/q + 刷新 buf_idx/w。
// ∂x_i = Σ_k δ[idx_k[i]]·q_i·w_k[i]
//      + δ[i+1]·l[i+1]·[i+1 < bOffset]      ← 仅 C/F 段经 x 传递；B 段的 l 项走 prevB ⟹ 对 x 无贡献
//      + δ[i-1]·r[i-1]·[i-1 < bOffset]      ← 同上
// ∂p_i = Σ_k δ[idx_k[i]]·x_i·q_i·(−2·d_k)，d_k = hp_p_i − hp_idx_k[i]
// ∂q_i = Σ_k δ[idx_k[i]]·x_i·w_k[i]
// 权重更新后 clamp buf_p[0, sizeA1-1]，刷新 buf_idx0/idx1/w0/w1（同 refresh_cache 逻辑）。
// StoreTc=true 时写 bufTc（GradCellOp 阶段一外拷输入梯度；**写入前经 I2 的 C 域投影**——见
// cnn_active_project_c_domain）；false 时跳过（编译期消除）。
// 注意：本 kernel 已是"在 kernel 内做域投影"的既有先例（buf_p 的 clamp），I2 与之同构。
// I3（L0.2）：buf_q 亦在此更新，故 q 的限幅落在此处（cnn_active_clamp_weight）。
//
// 注意：hp_p/q/idx/w 可能与 buf_p/q/idx/w 别名（bufHp==hp 时）。
// 不对 hp_p/q/idx/w 用 __ldg（可能被同 kernel 写入导致 texture cache 过期）。
// dz/x/prevB/hp_l/hp_r 只读且不与任何写别名，安全用 __ldg。
template <bool StoreTc>
__global__ void __launch_bounds__(BLOCK, 2)
cnn_active_backward_input_pq_kernel(
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

    int bOffset = sizeA1 - B_LEN;

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
    // ∂x_i: l 项（δ[i+1]·l[i+1]）——仅当输出位 i+1 仍在 C/F 段（i+1 < bOffset）时经 x 传递；
    //        B 段的 l 项读的是 prevB，对 x 无依赖。
    if (i + 1 < bOffset) {
        dx += __ldg(&dz[i + 1]) * __ldg(&hp_l[i + 1]);
    }
    // ∂x_i: r 项（δ[i-1]·r[i-1]）——同理，仅 C/F 段。
    if (i - 1 >= 0 && i - 1 < bOffset) {
        dx += __ldg(&dz[i - 1]) * __ldg(&hp_r[i - 1]);
    }

    dInput[i] = dx;
    // I2（L0.1）：**C2 = tC 的唯一产出点**。投影回 C 的合法值域 [−1,+1]
    // （推导与唯一性论证见文件头 cnn_active_project_c_domain 的说明）。
    // 只投 bufTc（C2 本体），**不投 dInput**：dInput 是整段输入梯度（C@F@G@dt 全 span，向上游传播），
    // 不参与自指环，且其本义就是"梯度"，不应被裁剪（真善美第1条"真"：不篡改本义）。
    if constexpr (StoreTc) {
        if (i < sizeC) {
            bufTc[i] = cnn_active_project_c_domain(dx);
        }
    }

    float pi = hp_p[i];

    // ∂p_i = Σ_k δ[idx_k[i]]·x_i·q_i·(−2·d_k)
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
    // I3（L0.2 权重限幅）：q 的更新点在此（p 是**位置**、已有自己的 [0,m-1] 域，不属权重限幅范畴）。
    buf_q[i] = cnn_active_clamp_weight(new_q);

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

extern "C" void cnn_active_backward_layer(
    const float* trace_z, const float* trace_y, const float* target, const float* x,
    const float* prevB, int activationId,
    const float* hp_p, const float* hp_q, const float* hp_l, const float* hp_r, const float* hp_b,
    const int* hp_idx0, const int* hp_idx1, const float* hp_w0, const float* hp_w1,
    int sizeA0, int sizeA1, int sizeC, float lr, cudaStream_t stream /* -> */,
    float* buf_p, float* buf_q, float* buf_l, float* buf_r, float* buf_b,
    int* buf_idx0, int* buf_idx1, float* buf_w0, float* buf_w1,
    float* dz, float* dInput, float* bufTc)
{
    // trace_z 本版**启用**（clipped-linear 的导数需 |z|<1 判定），与原 CNN 的 (void)trace_z 相反。
    // hp_b 仍不用（∂b_j = δ_j，不依赖 b 值）。
    (void)hp_b;

    if (trace_z == nullptr || trace_y == nullptr || target == nullptr || x == nullptr
        || prevB == nullptr || dz == nullptr || dInput == nullptr) {
        fprintf(stderr, "ERROR: null required pointer in cnn_active_backward_layer\n");
        abort();
    }

    dim3 block(BLOCK);
    dim3 grid1((unsigned int)((sizeA1 + BLOCK - 1) / BLOCK));
    dim3 grid2((unsigned int)((sizeA0 + BLOCK - 1) / BLOCK));

    // Kernel1: δ + buf_l/r/b 更新（同 stream，先于 Kernel2 完成）
    cnn_active_backward_delta_kernel<<<grid1, block, 0, stream>>>(
        trace_z, trace_y, target, x, prevB, activationId, sizeA0, sizeA1, lr /* -> */,
        buf_l, buf_r, buf_b, dz);

    // Kernel2: dInput + bufTc + buf_p/q 更新 + buf_idx/w 刷新
    // （读 dz 由 Kernel1 写入，同 stream 保证 Kernel1→Kernel2 顺序）
    // bufTc 非空时实例化 <true>（写 bufTc），为空时 <false>（跳过），编译期消除分支。
    if (bufTc != nullptr) {
        cnn_active_backward_input_pq_kernel<true><<<grid2, block, 0, stream>>>(
            dz, x, hp_p, hp_q, hp_l, hp_r, hp_idx0, hp_idx1, hp_w0, hp_w1,
            sizeA0, sizeA1, sizeC, lr /* -> */, buf_p, buf_q, buf_idx0, buf_idx1, buf_w0, buf_w1, dInput, bufTc);
    } else {
        cnn_active_backward_input_pq_kernel<false><<<grid2, block, 0, stream>>>(
            dz, x, hp_p, hp_q, hp_l, hp_r, hp_idx0, hp_idx1, hp_w0, hp_w1,
            sizeA0, sizeA1, sizeC, lr /* -> */, buf_p, buf_q, buf_idx0, buf_idx1, buf_w0, buf_w1, dInput, nullptr);
    }
}
