#pragma once
#include <cuda_runtime.h>
#include <stdint.h>

// 重要：参数必须使用 intptr_t（64 位），不能用 long。
// 在 Windows x64 (LLP64 模型) 下 long 仅有 32 位，会把 JNI 传入的 64 位指针/句柄
// 截断为 32 位，再 cast 回指针时发生符号扩展，得到 0xFFFFFFFF... 的无效内核地址，
// 触发 CUDA "illegal memory access"。
void bnn_forward_layer_bridge_storefz(
    intptr_t a_prev_pad, intptr_t q, intptr_t P,
    intptr_t l, intptr_t r, intptr_t b,
    intptr_t a_curr, intptr_t fz, intptr_t n, intptr_t n_words,
    intptr_t stream
);

void bnn_forward_layer_bridge_nofz(
    intptr_t a_prev_pad, intptr_t q, intptr_t P,
    intptr_t l, intptr_t r, intptr_t b,
    intptr_t a_curr, intptr_t n, intptr_t n_words,
    intptr_t stream
);
