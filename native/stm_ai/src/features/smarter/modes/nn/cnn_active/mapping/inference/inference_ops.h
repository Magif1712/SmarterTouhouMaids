#pragma once
#include <cuda_runtime.h>
#include <stdint.h>

// CNN-Active 前向（照抄原 CNN 的 inference_ops + 两处改：activationId + prevB）。
//
// 与原生 CNN 前向的差异（方案 §三 差异清单）：
//   D2 激活：σ(z) 换成 cnn_active_activate(z, activationId)（1=tanh 默认，2=clipped-linear）。
//            sigmoid 不在 Ext(D) 内——值域 (0,1) 无法表示有符号的 C 段 target（方案 §2.2）。
//   D4 邻域：B 段（j >= sizeA1 - 256）的 l/r 项改读"上一拍行为" prevB 的 16 位块内模环，
//            不再读 x[j±1]（顺带消掉原设计 18 位跨域泄漏：l 生效 18 位、r 16 位）。
//            C/F 段（j < bOffset）保持原空间邻接 x[j±1]，语义正确、零改动。
//
// 不变项（照抄）：push 稀疏投影（位置 p + 幅度 q + 三角插值权重 w）；l/r/b 的线性累加结构；
//                 越界补 0 的边界约定；idx/w 是 p 的派生缓存（由 cnn_active_refresh_cache 生产）。
//
// 为什么 refresh_cache 也要照抄而不是复用原 CNN 的（定理1(2)）：idx/w 是"生产者"，
// 本模块的 pull kernel 是"消费者"。两者必须同源于本模块，否则原 CNN 一旦演化插值约定，
// 本模块的被冻结消费者会静默失配。故 idx/w 派生逻辑随本模块一起独立演化。
//
// 入参在前，/* -> */ 之后是出参（设计原则第5条）。

// 带 trace（训练用）：z=trace_z 累加（push atomicAdd + pull_lr + b），trace_y 写激活值。
// 前向用 idx/w（持久缓存），不用 p（p 仅 refresh_cache/backward 用）。
extern "C" void cnn_active_forward_layer_trace(
    const float* x, const float* q, const float* l, const float* r, const float* b,
    const int* idx0, const int* idx1, const float* w0, const float* w1,
    const float* prevB, int activationId,
    int sizeA0, int sizeA1, cudaStream_t stream /* -> */,
    float* trace_z, float* trace_y);

// 无 trace（纯推理）：y 做工作区（push → pull → activate 覆盖 y）。
extern "C" void cnn_active_forward_layer_notrace(
    const float* x, const float* q, const float* l, const float* r, const float* b,
    const int* idx0, const int* idx1, const float* w0, const float* w1,
    const float* prevB, int activationId,
    int sizeA0, int sizeA1, cudaStream_t stream /* -> */,
    float* y);

// 缓存刷新：由 p 重算 idx0/idx1/w0/w1（非热路径，构造/loadFromFile 后一次性）。
extern "C" void cnn_active_refresh_cache(
    const float* p, int sizeA0, int sizeA1, cudaStream_t stream /* -> */,
    int* idx0, int* idx1, float* w0, float* w1);
