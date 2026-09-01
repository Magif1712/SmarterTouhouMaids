#include <iostream>
#include <vector>
#include <stdexcept>
#include <string>
#include "../Vector.h"
#include "../VectorMapping.h"
#include "vector_bridge.h"

// 设计说明：
// 所有 bridge 函数在捕获异常后，先打印诊断信息到 stderr，然后 **重新抛出** 异常。
// 这样 JNI 层的 try-catch (JNI_CATCH_TRANSLATE) 能将 C++ 异常转换为 Java RuntimeException，
// 让 Java 调用方感知失败，而不是让 CUDA 错误残留 (sticky) 导致后续操作雪崩式失败。
// 唯一不重抛的是 Create/Delete 系列：Create 必须返回指针（失败返回 nullptr），
// Delete 不应抛异常（类似析构）。
//
// stream 参数：D2D/H2D 拷贝与标量乘法 kernel 均接收 cudaStream_t，由调用方决定流归属。
// CopyToHost 仍为同步（cudaMemcpy），不带 stream——D2H 同步语义，且 Urana 运行时不用。

extern "C"
{
    // ====================================================================
    // Vector<bool> 接口
    // ====================================================================

    Vector<bool> *VectorCreateBool()
    {
        try
        {
            return new Vector<bool>();
        }
        catch (const std::exception &e)
        {
            std::cerr << "Error in VectorCreateBool: " << e.what() << std::endl;
            return nullptr;
        }
    }

    void VectorAllocateBool(Vector<bool> *vec, size_t size)
    {
        if (vec)
        {
            try
            {
                vec->allocate(size);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorAllocateBool: " << e.what() << std::endl;
                throw;
            }
        }
    }

    void VectorDeleteBool(Vector<bool> *vec)
    {
        delete vec;
    }

    void VectorCopyFromHostBool(Vector<bool> *vec, const uint32_t *h_data, size_t wordCount, cudaStream_t stream)
    {
        if (vec)
        {
            try
            {
                vec->copyFromHost(h_data, wordCount, stream);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorCopyFromHostBool: " << e.what() << std::endl;
                throw;
            }
        }
    }

    void VectorCopyToHostBool(Vector<bool> *vec, uint32_t *h_data, size_t wordCount)
    {
        if (vec)
        {
            try
            {
                vec->copyToHost(h_data, wordCount);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorCopyToHostBool: " << e.what() << std::endl;
                throw;
            }
        }
    }

    void VectorCopyToHostBoolSync(Vector<bool> *vec, uint32_t *h_data, size_t wordCount, cudaStream_t stream)
    {
        if (vec)
        {
            try
            {
                vec->copyToHost(h_data, wordCount, stream);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorCopyToHostBoolSync: " << e.what() << std::endl;
                throw;
            }
        }
    }

    void VectorAllocateBoolMapped(Vector<bool> *vec, size_t size)
    {
        if (vec)
        {
            try
            {
                vec->allocateMapped(size);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorAllocateBoolMapped: " << e.what() << std::endl;
                throw;
            }
        }
    }

    // 纯 host memcpy：把 mapped host 内存拷到 out。零 CUDA 调用，不 flush WDDM 命令缓冲。
    // 设计原则（真善美第 3 条）：behavior 就绪状态已由 mapped 内存固化，读取它无需任何 GPU 同步。
    void VectorReadMappedBool(Vector<bool> *vec, uint32_t *out, size_t wordCount)
    {
        if (!vec || !out || wordCount == 0)
            return;
        try
        {
            const uint32_t *host_ptr = vec->hostData();
            if (host_ptr == nullptr)
                throw std::runtime_error("Vector is not mapped; hostData() is null");
            if (wordCount > vec->wordCount())
                throw std::out_of_range("wordCount exceeds mapped vector capacity");
            std::memcpy(out, host_ptr, wordCount * sizeof(uint32_t));
        }
        catch (const std::exception &e)
        {
            std::cerr << "Error in VectorReadMappedBool: " << e.what() << std::endl;
            throw;
        }
    }

    void VectorSaveBool(Vector<bool> *vec, const std::filesystem::path &filename)
    {
        if (vec)
        {
            try
            {
                vec->save(filename);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorSaveBool: " << e.what() << std::endl;
                throw;
            }
        }
    }

    void VectorLoadFromFileBool(Vector<bool> *vec, const std::filesystem::path &filename)
    {
        if (vec)
        {
            try
            {
                vec->loadFromFile(filename);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorLoadFromFileBool: " << e.what() << std::endl;
                throw;
            }
        }
    }

    size_t VectorGetSizeBool(Vector<bool> *vec)
    {
        return vec ? vec->size() : 0;
    }

    void VectorSetRegionBool(Vector<bool> *dst, size_t dest_offset_bits, const Vector<bool> *src, cudaStream_t stream)
    {
        if (dst && src)
        {
            try
            {
                dst->setRegion(dest_offset_bits, *src, stream);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorSetRegionBool: " << e.what() << std::endl;
                throw;
            }
        }
    }

    void VectorCopyRegionFromBool(Vector<bool> *dst, size_t dest_offset_bits, const Vector<bool> *src, size_t src_offset_bits, size_t num_bits, cudaStream_t stream)
    {
        if (dst && src)
        {
            try
            {
                dst->copyRegionFrom(dest_offset_bits, *src, src_offset_bits, num_bits, stream);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorCopyRegionFromBool: " << e.what() << std::endl;
                throw;
            }
        }
    }

    void VectorCopyRegionFromHostBool(Vector<bool> *dst, size_t dest_offset, const unsigned char* src_data, size_t num_bits, cudaStream_t stream)
    {
        if (dst)
        {
            try
            {
                dst->copyRegionFromHost(dest_offset, src_data, num_bits, stream);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorCopyRegionFromHostBool: " << e.what() << std::endl;
                throw;
            }
        }
    }

    // ===================================================================
    // Vector<int> 接口
    // ===================================================================

    Vector<int> *VectorCreateInt()
    {
        try
        {
            return new Vector<int>();
        }
        catch (const std::exception &e)
        {
            std::cerr << "Error in VectorCreateInt: " << e.what() << std::endl;
            return nullptr;
        }
    }

    void VectorAllocateInt(Vector<int> *vec, size_t size)
    {
        if (vec)
        {
            try
            {
                vec->allocate(size);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorAllocateInt: " << e.what() << std::endl;
                throw;
            }
        }
    }

    void VectorDeleteInt(Vector<int> *vec)
    {
        delete vec;
    }

    void VectorCopyFromHostInt(Vector<int> *vec, const int *h_data, size_t count, cudaStream_t stream)
    {
        if (vec)
        {
            try
            {
                vec->copyFromHost(h_data, count, stream);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorCopyFromHostInt: " << e.what() << std::endl;
                throw;
            }
        }
    }

    void VectorCopyToHostInt(Vector<int> *vec, int *h_data, size_t count)
    {
        if (vec)
        {
            try
            {
                vec->copyToHost(h_data, count);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorCopyToHostInt: " << e.what() << std::endl;
                throw;
            }
        }
    }

    void VectorSaveInt(Vector<int> *vec, const std::filesystem::path &filename)
    {
        if (vec)
        {
            try
            {
                vec->save(filename);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorSaveInt: " << e.what() << std::endl;
                throw;
            }
        }
    }

    void VectorLoadFromFileInt(Vector<int> *vec, const std::filesystem::path &filename)
    {
        if (vec)
        {
            try
            {
                vec->loadFromFile(filename);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorLoadFromFileInt: " << e.what() << std::endl;
                throw;
            }
        }
    }

    size_t VectorGetSizeInt(Vector<int> *vec)
    {
        return vec ? vec->size() : 0;
    }

    void VectorCopyRegionFromInt(Vector<int>* dst, size_t dst_offset, const Vector<int>* src, size_t src_offset, size_t num_elements, cudaStream_t stream)
    {
        if (dst && src)
        {
            try
            {
                dst->copyRegionFrom(dst_offset, *src, src_offset, num_elements, stream);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorCopyRegionFromInt: " << e.what() << std::endl;
                throw;
            }
        }
    }

    void VectorSetRegionInt(Vector<int>* dst, size_t dest_offset, const Vector<int>* src, cudaStream_t stream)
    {
        if (dst && src)
        {
            try
            {
                dst->setRegion(dest_offset, *src, stream);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorSetRegionInt: " << e.what() << std::endl;
                throw;
            }
        }
    }

    void VectorCopyRegionFromHostInt(Vector<int>* dst, size_t dest_offset, const int* src_host_data, size_t num_elements, cudaStream_t stream)
    {
        if (dst && src_host_data)
        {
            try
            {
                dst->copyRegionFromHost(dest_offset, src_host_data, num_elements, stream);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorCopyRegionFromHostInt: " << e.what() << std::endl;
                throw;
            }
        }
    }

    // ===================================================================
    // 算法接口
    // ===================================================================

    void VectorScatterBits(Vector<bool> *src, Vector<bool> *dst, Vector<int> *P)
    {
        if (src && dst && P)
        {
            try
            {
                scatterBits(*src, *dst, *P);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorScatterBits: " << e.what() << std::endl;
                throw;
            }
        }
    }

    void VectorXorBool(Vector<bool> *dst, Vector<bool> *src)
    {
        if (dst && src)
        {
            try
            {
                xorVectors(*dst, *src);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorXorBool: " << e.what() << std::endl;
                throw;
            }
        }
    }

    void VectorSubtractBool(
        const Vector<bool>* a,
        const Vector<bool>* b,
        long long bit_offset,
        Vector<int>* c,
        long long c_int_offset,
        long long bit_length,
        cudaStream_t stream)
    {
        if (a && b && c)
        {
            try
            {
                subtractBoolVectors(a->data(), b->data(), bit_offset, c->data(), c_int_offset, bit_length, stream);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorSubtractBool: " << e.what() << std::endl;
                throw;
            }
        }
    }

    void VectorMultiplyByScalarInt(Vector<int> *vector, int scalar, size_t offset, size_t length, cudaStream_t stream)
    {
        if (vector)
        {
            try
            {
                multiplyVectorByScalarInPlace(*vector, scalar, offset, length, stream);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorMultiplyByScalarInt: " << e.what() << std::endl;
                throw;
            }
        }
    }

    // 用 PCG 随机填充位向量（BNN 权重初始化）。
    // 同步语义：launch 后 cudaStreamSynchronize(0)，保证 Hyperparameters 构造返回时权重已写完——
    // SmarterClientService.init 在构造 UranaSystem 后才 awaken 启动工作线程，故权重必在工作线程
    // 首轮 forward 前就绪。init 一次性开销（非 per-tick 热路径），不触发热路径 WDDM flush 问题。
    void VectorFillRandomBool(Vector<bool> *vec, uint64_t seed)
    {
        if (vec)
        {
            try
            {
                fillRandomBits(*vec, seed, 0);
                cudaStreamSynchronize(0);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorFillRandomBool: " << e.what() << std::endl;
                throw;
            }
        }
    }

    // 用 PCG 随机填充整数向量，元素 ∈ [0, maxVal)（BNN 目标索引 P 初始化）。同步语义同上。
    void VectorFillRandomInt(Vector<int> *vec, int maxVal, uint64_t seed)
    {
        if (vec)
        {
            try
            {
                fillRandomInts(*vec, maxVal, seed, 0);
                cudaStreamSynchronize(0);
            }
            catch (const std::exception &e)
            {
                std::cerr << "Error in VectorFillRandomInt: " << e.what() << std::endl;
                throw;
            }
        }
    }
}
