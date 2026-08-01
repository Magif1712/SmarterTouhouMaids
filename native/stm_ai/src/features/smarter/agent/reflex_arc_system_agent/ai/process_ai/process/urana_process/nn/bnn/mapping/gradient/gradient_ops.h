#pragma once
#include <cuda_runtime.h>
#include <cstdint>


/**
 * @brief 梯度符号翻转算子主机端调用入口
 */
cudaError_t gradientSignFlip(
    int* __restrict__ dz,
    const int* __restrict__ da,
    const uint32_t* __restrict__ fz_packed,
    const uint32_t* __restrict__ b_packed,
    size_t n,
    cudaStream_t stream = 0
);

/**
 * @brief 激活反向传播核函数的主机调用入口
 */
void backwardActivation(
    const int32_t*  __restrict__ dz,
    const int32_t*  __restrict__ p,
    const uint32_t* __restrict__ q,
    const uint32_t* __restrict__ l,
    const uint32_t* __restrict__ r,
    int32_t*        __restrict__ da,
    int batch_size,
    int n_curr,
    int n_prev,
    cudaStream_t stream = 0
);

cudaError_t backwardLayer(
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
);

// --- In-place Gradient Descent ---
cudaError_t backwardActivationAndGradientDescent(
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
    cudaStream_t stream
);

cudaError_t backwardGradientDescentLayer(
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
    cudaStream_t stream
);