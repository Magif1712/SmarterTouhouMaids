#pragma once
#include "Vector.h"
#include <cuda_runtime.h>

/**
 * @brief 将源位向量 A 中的每一位按照索引映射 P 分散写入目标位向量 B。
 */
void scatterBits(const Vector<bool> &src, Vector<bool> &dst, const Vector<int> &P);

/**
 * @brief 执行 A ^= B 操作。
 */
void xorVectors(Vector<bool> &A, const Vector<bool> &B);

/**
 * @brief [区间操作] 从两个位压缩的布尔向量计算差值，并将结果存入一个整数向量的指定区间。
 * @param d_A           被减数布尔向量的设备指针
 * @param d_B           减数布尔向量的设备指针
 * @param bit_offset    d_A 和 d_B 中开始计算的比特偏移量
 * @param d_C           目标整数向量的设备指针
 * @param c_int_offset  d_C 中开始写入的整数偏移量
 * @param bit_length    要处理的比特数
 * @param stream        CUDA 流
 */
void subtractBoolVectors(
    const uint32_t* d_A,
    const uint32_t* d_B,
    int64_t         bit_offset,
    int32_t*        d_C,
    int64_t         c_int_offset,
    int64_t         bit_length,
    cudaStream_t    stream = 0);

/**
 * @brief 对 Vector<T> 执行原地标量乘法 (vector *= scalar)。
 */
template <typename T>
void multiplyVectorByScalarInPlace(
    Vector<T>& vector,
    T scalar,
    size_t offset,
    size_t length,
    cudaStream_t stream = 0);

/**
 * @brief 用 PCG 哈希随机填充位向量：每个 32-bit word 填一个 PCG 随机值 = 32 个独立随机 bit。
 *        用于 BNN 权重初始化。零权重会使网络确定性死亡（零吸引子：零权重→零输出→零目标→零梯度），
 *        故权重必须随机起步。不依赖 curand，GPU 原地填充，零额外显存。
 * @param vec    目标位向量（须已分配）。
 * @param seed   64 位种子（内部折叠为 32 位喂给 PCG）。
 * @param stream CUDA 流。
 */
void fillRandomBits(Vector<bool>& vec, uint64_t seed, cudaStream_t stream = 0);

/**
 * @brief 用 PCG 哈希随机填充整数向量，每个元素 ∈ [0, maxVal)。
 *        用于 BNN 目标索引 P 的初始化：P 非置换，是随机散射目标（见 inference_ops.cu 的 bnn_push_p），
 *        随机化后输入 bit 均匀散射到输出空间各处，避免全零 P 把所有输入挤到 bit 0。
 * @param vec     目标整数向量（须已分配）。
 * @param maxVal  上界（独占）；maxVal<=0 时填 0。
 * @param seed    64 位种子。
 * @param stream  CUDA 流。
 */
void fillRandomInts(Vector<int>& vec, int maxVal, uint64_t seed, cudaStream_t stream = 0);

/**
 * @brief 用 PCG 哈希随机填充浮点向量，每个元素 ∈ [0, bound)。
 *        用于 CNN 权重初始化（与 BNN 同理——零权重→零输出→零梯度→权重永不更新的零吸引子）。
 * @param vec    目标浮点向量（须已分配）。
 * @param bound  上界（独占）；bound<=0 时填 0。
 * @param seed   64 位种子。
 * @param stream CUDA 流。
 */
void fillRandomFloats(Vector<float>& vec, float bound, uint64_t seed, cudaStream_t stream = 0);