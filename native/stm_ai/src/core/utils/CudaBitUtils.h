#pragma once

#include <cuda_runtime.h>
#include <cstddef> // for size_t

namespace stm_ai {
namespace core {
namespace utils {

/**
 * @brief [CUDA 工具] 高性能地将一个设备端位数组，复制到另一个设备端位数组的任意位偏移处。
 *
 * 这是一个底层的、操作裸指针的位操作工具。它不关心数据从何而来，只负责高效、精确地
 * 完成位复制任务。该函数是异步的，会向指定的 CUDA 流中提交一个 Kernel。
 *
 * @param dst 指向目标设备缓冲区的指针。不允许为 nullptr。
 * @param src 指向源设备缓冲区的指针。不允许为 nullptr。
 * @param dst_offset_bits 目标缓冲区中的起始位偏移。可以是任意非负值。
 * @param src_num_bits 要从源缓冲区复制的位数。如果为 0，则函数不执行任何操作。
 * @param stream 用于异步执行的 CUDA 流。默认为 0 (默认流)。
 *
 * @throws std::invalid_argument 如果 dst 或 src 为 nullptr。
 */
void CudaBitCopy(
    uint32_t* dst,
    size_t dst_size_bits,      // 目标总位数
    size_t dst_offset_bits,    // 目标起始位偏移
    const uint32_t* src,
    size_t src_size_bits,      // 源总位数
    size_t src_offset_bits,    // 源起始位偏移
    size_t num_bits,           // 要复制的位数
    cudaStream_t stream = 0
);

/**
 * @brief [CUDA 工具] 从 Host (CPU) 向设备端位数组的任意位偏移处复制数据。
 *
 * 这个函数处理从一个非紧凑的 `unsigned char` 数组 (每个布尔值占一个字节)
 * 到一个位压缩的设备端数组的转换和复制。
 *
 * @param dst_device 指向目标设备缓冲区的指针。
 * @param dst_size_bits 目标设备缓冲区的总位数。
 * @param dst_offset_bits 目标缓冲区中的起始位偏移。
 * @param src_host 指向源 Host 数据的指针 (jboolean* 或 unsigned char*)。
 * @param num_bits 要复制的位数。
 * @param stream 用于异步执行的 CUDA 流。
 */
void CudaBitCopyFromHost(
    uint32_t* dst_device,
    size_t dst_size_bits,
    size_t dst_offset_bits,
    const unsigned char* src_host,
    size_t num_bits,
    cudaStream_t stream = 0
);

} // namespace utils
} // namespace core
} // namespace stm_ai