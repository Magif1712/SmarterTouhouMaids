#pragma once
#include <cuda_runtime.h>
#include <cstdint>

// CNN-Active 反向传播两阶段 host 入口（照抄原 CNN 的 gradient_ops + 两处改）。
//
// 与原生 CNN 反向的差异（方案 §三 差异清单 / §7.2）：
//   D2 激活导数：a′(z) 依 activationId 选择——
//        1=tanh：1 − y²（只用 y，无需 z）
//        2=clipped-linear：(|z| < 1) ? 1 : 0（需读 z）
//        δ = 2(y − t) · a′(z)。原 backward 显式 (void)trace_z；本版**启用 trace_z**（clipped 需要）。
//   D4 邻域：∂l_j/∂r_j 的输入特征取自 cnn_active_neighbors——
//        B 段（j >= sizeA1 - 256）取 prevB 的 16 位块内模环；C/F 段取 x[j±1]。
//
// 附带修正（D4 的必然后果，方案 §1.2 第3点）：原设计 B 段的 l/r 读 x[j±1] 落进 G/dt，
// 于是 ∂x 里含 δ[j]·l[j] / δ[j]·r[j] 这两项。B 段改读 prevB 后 z_j 不再依赖 x，
// 故 ∂x_i 的 l/r 项必须加"j < bOffset"守卫，否则梯度与真实前向不一致（f 不再是它自己的导数）。
// 这 18 位跨域泄漏随 D4 一并消失。
//
// Kernel1（sizeA1）：δ_j = 2(y−t)·a′(z) → dz[j]；∂l/∂r = δ·邻域真值（B 段 prevB）；∂b = δ；
//                    buf_l/r/b -= lr·grad。
// Kernel2（sizeA0）：∂x_i = Σ_k δ[idx_k[i]]·q·w_k + δ[i+1]·l[i+1]·[i+1<bOffset]
//                                             + δ[i-1]·r[i-1]·[i-1<bOffset] → dInput + bufTc；
//                    ∂p/∂q 同原式；buf_p/q -= lr·grad；clamp buf_p；刷新 buf_idx/w。
//
// L0.2 稳定不变量（本版追加，方案 §2 两级表 / §5.1 裁决）：
//   I3 权重限幅——q/l/r/b 更新后投影到 [−W, W]（W = WEIGHT_LIMIT，gradient_ops.cu 内的结构常量）；
//   I4 B 段自环收缩——仅 j >= bOffset 保证 |l_j|+|r_j| ≤ LOOP_GAIN_CAP < 1（超限则同比例缩）；
//   I2 C 域投影——bufTc（C2）写入前投影到 [−1,+1]（见 gradient_ops.cu 文件头的
//   cnn_active_project_c_domain）。
//   三者均为**结构常量、不暴露、不设第二读路径** ⇒ 本签名**不变**（零 bridge / 零 Java 改动）。
//   完整推导见 gradient_ops.cu 文件头 "L0.1 / L0.2" 常量说明。
//
// 训练：截断式单步递归（方案 §1.5）——不把梯度回溯到上一拍的 y，故不做 BPTT。
// 把 prevB 视为 X 的一部分（正如系统早已把"上一轮输出的 C 段"当作输入的一部分）
// ⟹ f: Ext(X ∪ {prevB}) → Ext(G) 仍是单一映射，公理(1) 满足。
//
// trace_z 本版启用（clipped 导数需要）；hp_b 仍不用（∂b_j = δ_j，不依赖 b 值）。
// bufTc 为 nullptr 时跳过外拷输入梯度。入参在前，/* -> */ 之后是出参（设计原则第5条）。
extern "C" void cnn_active_backward_layer(
    const float* trace_z, const float* trace_y, const float* target, const float* x,
    const float* prevB, int activationId,
    const float* hp_p, const float* hp_q, const float* hp_l, const float* hp_r, const float* hp_b,
    const int* hp_idx0, const int* hp_idx1, const float* hp_w0, const float* hp_w1,
    int sizeA0, int sizeA1, int sizeC, float lr, cudaStream_t stream /* -> */,
    float* buf_p, float* buf_q, float* buf_l, float* buf_r, float* buf_b,
    int* buf_idx0, int* buf_idx1, float* buf_w0, float* buf_w1,
    float* dz, float* dInput, float* bufTc);
