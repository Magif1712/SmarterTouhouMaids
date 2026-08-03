#ifndef ELASTIC_OPS_H
#define ELASTIC_OPS_H

#include <cuda_runtime.h>
#include <cstdint>
#include <cstddef>

/**
 * @brief 输入变化门控重连：当输入位发生变化时，重连对应的断连 q 通道。
 *
 * 惊跳反射（startle reflex）的核心操作。对每个位 i：
 *   changed[i] = currentInput[i] XOR prevInput[i]
 *   if changed[i] AND q[i] == 0:
 *       q[i] = 1   // 重连断连的通道
 *
 * 静止期：输入不变 → 不触发重连 → AI 安静睡眠（合理）
 * 环境变化：输入变化 → 精确重连 → AI 苏醒
 *
 * @param q_data          q 权重 bit-packed 数据（原地修改：断连位可能被重连）
 * @param input_data      当前输入 bit-packed 数据
 * @param prev_data       上一步输入 bit-packed 数据（调用后更新为 input_data 的副本）
 * @param num_bits        位数
 * @param num_words       word 数（ceil(num_bits / 32)）
 * @param stream          CUDA 流
 * @return cudaError_t    成功返回 cudaSuccess
 */
cudaError_t reconnectOnInputChange(
    uint32_t*       __restrict__ q_data,
    const uint32_t* __restrict__ input_data,
    uint32_t*       __restrict__ prev_data,
    size_t          num_bits,
    size_t          num_words,
    cudaStream_t    stream = 0);

#endif // ELASTIC_OPS_H
