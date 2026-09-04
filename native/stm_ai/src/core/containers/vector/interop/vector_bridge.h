#pragma once
#include <cstddef>
#include <cstdint>
#include <cuda_runtime.h>
#include <filesystem>

// 前向声明，避免包含完整的 Vector 头文件
template <typename T>
class Vector;

extern "C"
{
    // Vector<bool> 接口
    Vector<bool> *VectorCreateBool();
    void VectorAllocateBool(Vector<bool> *vec, size_t size);
    void VectorDeleteBool(Vector<bool> *vec);
    void VectorCopyFromHostBool(Vector<bool> *vec, const uint32_t *h_data, size_t wordCount, cudaStream_t stream);
    void VectorCopyToHostBool(Vector<bool> *vec, uint32_t *h_data, size_t wordCount);
    // 指定 stream 上的同步 D2H：单流同步，不 drain 其它流（如 GL 渲染流）。供效应器工作线程读出 behavior。
    void VectorCopyToHostBoolSync(Vector<bool> *vec, uint32_t *h_data, size_t wordCount, cudaStream_t stream);
    // 分配为 host mapped pinned memory（zero-copy）：GPU 经 device 视图写等价于写 host 内存，host 直接读。
    void VectorAllocateBoolMapped(Vector<bool> *vec, size_t size);
    // 把 mapped host 内存的前 wordCount 个 word 拷贝到 out（纯 host memcpy，零 CUDA 调用，不 flush WDDM）。
    void VectorReadMappedBool(Vector<bool> *vec, uint32_t *out, size_t wordCount);
    void VectorSaveBool(Vector<bool> *vec, const std::filesystem::path &filename);
    void VectorLoadFromFileBool(Vector<bool> *vec, const std::filesystem::path &filename);
    size_t VectorGetSizeBool(Vector<bool> *vec);
    void VectorSetRegionBool(Vector<bool> *dst, size_t dest_offset_bits, const Vector<bool> *src, cudaStream_t stream);
    void VectorCopyRegionFromBool(Vector<bool> *dst, size_t dest_offset_bits, const Vector<bool> *src, size_t src_offset_bits, size_t num_bits, cudaStream_t stream);
    void VectorCopyRegionFromHostBool(Vector<bool> *dst, size_t dest_offset, const unsigned char* src_data, size_t num_bits, cudaStream_t stream);

    // Vector<int> 接口
    Vector<int> *VectorCreateInt();
    void VectorAllocateInt(Vector<int> *vec, size_t size);
    void VectorDeleteInt(Vector<int> *vec);
    void VectorCopyFromHostInt(Vector<int> *vec, const int *h_data, size_t count, cudaStream_t stream);
    void VectorCopyToHostInt(Vector<int> *vec, int *h_data, size_t count);
    void VectorSaveInt(Vector<int> *vec, const std::filesystem::path &filename);
    void VectorLoadFromFileInt(Vector<int> *vec, const std::filesystem::path &filename);
    size_t VectorGetSizeInt(Vector<int> *vec);
    void VectorCopyRegionFromInt(Vector<int>* dst, size_t dst_offset, const Vector<int>* src, size_t src_offset, size_t num_elements, cudaStream_t stream);
    void VectorSetRegionInt(Vector<int>* dst, size_t dest_offset, const Vector<int>* src, cudaStream_t stream);
    void VectorCopyRegionFromHostInt(Vector<int>* dst, size_t dest_offset, const int* src_host_data, size_t num_elements, cudaStream_t stream);

    // Vector<float> 接口（与 Vector<int> 对称：CNN 浮点权重/激活/梯度）
    Vector<float> *VectorCreateFloat();
    void VectorAllocateFloat(Vector<float> *vec, size_t size);
    void VectorDeleteFloat(Vector<float> *vec);
    void VectorCopyFromHostFloat(Vector<float> *vec, const float *h_data, size_t count, cudaStream_t stream);
    void VectorCopyToHostFloat(Vector<float> *vec, float *h_data, size_t count);
    void VectorSaveFloat(Vector<float> *vec, const std::filesystem::path &filename);
    void VectorLoadFromFileFloat(Vector<float> *vec, const std::filesystem::path &filename);
    size_t VectorGetSizeFloat(Vector<float> *vec);
    void VectorCopyRegionFromFloat(Vector<float>* dst, size_t dst_offset, const Vector<float>* src, size_t src_offset, size_t num_elements, cudaStream_t stream);
    void VectorSetRegionFloat(Vector<float>* dst, size_t dest_offset, const Vector<float>* src, cudaStream_t stream);
    void VectorCopyRegionFromHostFloat(Vector<float>* dst, size_t dst_offset, const float* src_host_data, size_t num_elements, cudaStream_t stream);

    // 分配为 host mapped pinned memory（zero-copy）：GPU 经 device 视图写等价于写 host 内存，host 直接读。
    void VectorAllocateFloatMapped(Vector<float> *vec, size_t size);
    // 纯 host memcpy：把 mapped host 内存的前 count 个 float 拷贝到 out（零 CUDA 调用，不 flush WDDM）。
    void VectorReadMappedFloat(Vector<float> *vec, float *out, size_t count);

    // 算法接口
    void VectorScatterBits(Vector<bool> *src, Vector<bool> *dst, Vector<int> *P);
    void VectorXorBool(Vector<bool> *dst, Vector<bool> *src);
    void VectorSubtractBool(
        const Vector<bool>* a,
        const Vector<bool>* b,
        long long bit_offset,
        Vector<int>* c,
        long long c_int_offset,
        long long bit_length,
        cudaStream_t stream);

    void VectorMultiplyByScalarInt(Vector<int> *vector, int scalar, size_t offset, size_t length, cudaStream_t stream);
    void VectorMultiplyByScalarFloat(Vector<float> *vector, float scalar, size_t offset, size_t length, cudaStream_t stream);

    // 用 PCG 随机填充位向量（BNN 权重初始化）。同步语义：launch 后 cudaStreamSynchronize(0)，
    // 保证构造期权重在 Urana 工作线程启动前写完。init 一次性开销，非热路径。
    void VectorFillRandomBool(Vector<bool> *vec, uint64_t seed);
    // 用 PCG 随机填充整数向量，元素 ∈ [0, maxVal)（BNN 目标索引 P 初始化）。同上同步语义。
    void VectorFillRandomInt(Vector<int> *vec, int maxVal, uint64_t seed);
    // 用 PCG 随机填充浮点向量，元素 ∈ [0, bound)（CNN 权重初始化）。同上同步语义。
    void VectorFillRandomFloat(Vector<float> *vec, float bound, uint64_t seed);
}