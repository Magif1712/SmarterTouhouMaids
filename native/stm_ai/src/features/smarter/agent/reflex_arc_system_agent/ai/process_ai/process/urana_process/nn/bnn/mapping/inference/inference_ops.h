#pragma once
#include <cuda_runtime.h>
#include <stdint.h>

extern "C" void bnn_forward_layer_storefz(
    const uint32_t* a_prev_pad, const uint32_t* q, const int32_t* P,
    const uint32_t* l, const uint32_t* r, const uint32_t* b,
    uint32_t* a_curr, uint32_t* fz, size_t n, size_t n_words,
    cudaStream_t stream);

extern "C" void bnn_forward_layer_nofz(
    const uint32_t* a_prev_pad, const uint32_t* q, const int32_t* P,
    const uint32_t* l, const uint32_t* r, const uint32_t* b,
    uint32_t* a_curr, size_t n, size_t n_words,
    cudaStream_t stream);