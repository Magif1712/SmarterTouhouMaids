#pragma once
#include <cstdint>

// CNN 反向 bridge（参数全用 intptr_t，Windows x64 LLP64 安全）。
// buf_* 句柄始终有效（GradCellOp 两阶段都更新权重）。
// bufTc 为 0 时传 nullptr（跳过外拷输入梯度）。
void cnn_backward_layer_bridge(
    intptr_t traceZ, intptr_t traceY, intptr_t target, intptr_t x,
    intptr_t hp_p, intptr_t hp_q, intptr_t hp_l, intptr_t hp_r, intptr_t hp_b,
    intptr_t hp_idx0, intptr_t hp_idx1, intptr_t hp_w0, intptr_t hp_w1,
    int sizeA0, int sizeA1, int sizeC, float lr, intptr_t stream /* -> */,
    intptr_t buf_p, intptr_t buf_q, intptr_t buf_l, intptr_t buf_r, intptr_t buf_b,
    intptr_t buf_idx0, intptr_t buf_idx1, intptr_t buf_w0, intptr_t buf_w1,
    intptr_t dz, intptr_t dInput, intptr_t bufTc);
