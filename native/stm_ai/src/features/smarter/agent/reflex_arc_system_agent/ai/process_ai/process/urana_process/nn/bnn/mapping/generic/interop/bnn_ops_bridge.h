#ifndef BNN_OPS_BRIDGE_H
#define BNN_OPS_BRIDGE_H

#include <cuda_runtime.h>

// 前向声明，避免在头文件中暴露 Vector 的完整实现
template<typename T>
class Vector;

/**
 * @brief negateAndBinarize 的桥接函数，使用 Vector 进行封装。
 *
 * @param[out] dst    目标 Vector<bool>
 * @param[in]  src    源 Vector<int>
 * @param[in]  stream CUDA 流（kernel 在此流上执行，与视觉采集流并发）
 */
void negateAndBinarizeBridge(Vector<bool>* dst, const Vector<int>* src, cudaStream_t stream);

/**
 * @brief negateAndBinarizeRegion 的桥接函数，使用 Vector 进行封装。
 *
 * @param[out] dst          目标 Vector<bool>
 * @param[in]  dst_offset   目标 Vector 的起始偏移（以元素/位为单位）
 * @param[in]  src          源 Vector<int>
 * @param[in]  src_offset   源 Vector 的起始偏移（以元素为单位）
 * @param[in]  n            要处理的元素数量
 * @param[in]  stream       CUDA 流
 */
void negateAndBinarizeRegionBridge(
    Vector<bool>*      dst,
    size_t             dst_offset,
    const Vector<int>* src,
    size_t             src_offset,
    size_t             n,
    cudaStream_t       stream
);

#endif // BNN_OPS_BRIDGE_H