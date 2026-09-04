#pragma once
#include <cuda_runtime.h>
#include <device_atomic_functions.h>
#include <stdexcept>
#include <string>
#include <vector>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <filesystem>

#include "core/utils/CudaBitUtils.h"

template <typename T>
class Vector
{
private:
    T *d_data;
    T *h_data;   // 非 mapped 时恒为 nullptr；mapped 时指向 host pinned 内存
    size_t m_size;
    bool m_isMapped; // 默认 false；仅 allocateMapped() 置 true

    static void checkCudaError(cudaError_t err, const char *msg)
    {
        if (err != cudaSuccess)
        {
            throw std::runtime_error(std::string(msg) + ": " + cudaGetErrorString(err));
        }
    }

    void release() noexcept
    {
        if (m_isMapped)
        {
            if (h_data != nullptr)
            {
                cudaFreeHost(h_data);
                h_data = nullptr;
            }
            d_data = nullptr;
            m_isMapped = false;
        }
        else if (d_data != nullptr)
        {
            cudaFree(d_data);
            d_data = nullptr;
        }
        m_size = 0;
    }

public:
    Vector() : d_data(nullptr), h_data(nullptr), m_size(0), m_isMapped(false) {}

    explicit Vector(size_t size) : d_data(nullptr), h_data(nullptr), m_size(size), m_isMapped(false)
    {
        if (m_size > 0)
        {
            cudaError_t err = cudaMalloc(reinterpret_cast<void **>(&d_data), bytes());
            checkCudaError(err, "Failed to allocate device memory for Vector");
            cudaMemset(d_data, 0, bytes());
        }
    }

    ~Vector() { release(); }

    Vector(const Vector &) = delete;
    Vector &operator=(const Vector &) = delete;

    Vector(Vector &&other) noexcept : d_data(other.d_data), h_data(other.h_data), m_size(other.m_size), m_isMapped(other.m_isMapped)
    {
        other.d_data = nullptr;
        other.h_data = nullptr;
        other.m_size = 0;
        other.m_isMapped = false;
    }

    Vector &operator=(Vector &&other) noexcept
    {
        if (this != &other)
        {
            release();
            d_data = other.d_data;
            h_data = other.h_data;
            m_size = other.m_size;
            m_isMapped = other.m_isMapped;
            other.d_data = nullptr;
            other.h_data = nullptr;
            other.m_size = 0;
            other.m_isMapped = false;
        }
        return *this;
    }

    void allocate(size_t new_size)
    {
        if (m_size == new_size) return;

        release();
        m_size = new_size;
        if (m_size > 0)
        {
            cudaError_t err = cudaMalloc(reinterpret_cast<void **>(&d_data), bytes());
            checkCudaError(err, "Failed to allocate device memory in Vector::allocate()");
            cudaMemset(d_data, 0, bytes());
        }
    }

    void allocateMapped(size_t new_size)
    {
        release();
        m_size = new_size;
        m_isMapped = true;
        if (m_size > 0)
        {
            cudaError_t err = cudaHostAlloc(reinterpret_cast<void **>(&h_data), bytes(), cudaHostAllocMapped);
            checkCudaError(err, "Failed to allocate mapped host memory for Vector");
            std::memset(h_data, 0, bytes());
            err = cudaHostGetDevicePointer(reinterpret_cast<void **>(&d_data), h_data, 0);
            checkCudaError(err, "Failed to get device pointer for mapped Vector");
        }
    }

    T *hostData() { return h_data; }
    const T *hostData() const { return h_data; }
    bool isMapped() const { return m_isMapped; }

    T *data() { return d_data; }
    const T *data() const { return d_data; }
    size_t size() const { return m_size; }
    size_t bytes() const { return m_size * sizeof(T); }

    void copyFromHost(const T *h_data, size_t count, cudaStream_t stream = 0)
    {
        copyRegionFromHost(0, h_data, count, stream);
    }

    void copyToHost(T *h_data, size_t count) const
    {
        if (count > m_size)
            throw std::out_of_range("Count exceeds vector size");
        if (count == 0)
            return;
        if (h_data == nullptr)
            throw std::invalid_argument("Host data pointer must not be null when count is positive");

        cudaError_t err = cudaMemcpy(h_data, d_data, count * sizeof(T), cudaMemcpyDeviceToHost);
        checkCudaError(err, "Failed to copy device to host (Vector)");
    }

    void copyRegionFrom(size_t dest_offset, const Vector<T>& source, size_t src_offset, size_t count, cudaStream_t stream = 0)
    {
        if (count == 0) return;

        if (dest_offset + count > m_size)
        {
            throw std::out_of_range("copyRegionFrom would write out of bounds of the destination vector.");
        }
        if (src_offset + count > source.size())
        {
            throw std::out_of_range("copyRegionFrom would read out of bounds of the source vector.");
        }

        cudaError_t err = cudaMemcpyAsync(
            d_data + dest_offset,
            source.data() + src_offset,
            count * sizeof(T),
            cudaMemcpyDeviceToDevice,
            stream
        );
        checkCudaError(err, "Failed to copy region between vectors");
    }

    void setRegion(size_t dest_offset, const Vector<T>& source, cudaStream_t stream = 0)
    {
        if (source.size() == 0) return;
        copyRegionFrom(dest_offset, source, 0, source.size(), stream);
    }

    void copyRegionFromHost(size_t dest_offset, const T* h_data, size_t count, cudaStream_t stream = 0)
    {
        if (count == 0) return;

        if (dest_offset + count > m_size)
        {
            throw std::out_of_range("copyRegionFromHost would write out of bounds of the destination vector.");
        }
        if (h_data == nullptr)
        {
            throw std::invalid_argument("Host data pointer must not be null when count is positive");
        }

        cudaError_t err = cudaMemcpyAsync(
            d_data + dest_offset,
            h_data,
            count * sizeof(T),
            cudaMemcpyHostToDevice,
            stream
        );
        checkCudaError(err, "Failed to copy region from host to vector");
    }

    void save(const std::filesystem::path &filename) const
    {
        std::ofstream ofs(filename, std::ios::binary);
        if (!ofs)
            throw std::runtime_error("Failed to open file for saving: " + filename.u8string());

        ofs.write(reinterpret_cast<const char *>(&m_size), sizeof(m_size));

        if (m_size > 0)
        {
            std::vector<T> h_data(m_size);
            copyToHost(h_data.data(), m_size);
            ofs.write(reinterpret_cast<const char *>(h_data.data()), bytes());
        }
    }

    void loadFromFile(const std::filesystem::path &filename)
    {
        std::ifstream ifs(filename, std::ios::binary);
        if (!ifs)
            throw std::runtime_error("Failed to open file for loading: " + filename.u8string());

        size_t saved_size;
        ifs.read(reinterpret_cast<char *>(&saved_size), sizeof(saved_size));

        allocate(saved_size);

        if (saved_size > 0)
        {
            std::vector<T> h_data(saved_size);
            ifs.read(reinterpret_cast<char *>(h_data.data()), bytes());
            if (!ifs)
                throw std::runtime_error("Error reading data from file: " + filename.u8string());
            copyFromHost(h_data.data(), saved_size);
        }
    }
};

template <>
class Vector<bool>
{
private:
    uint32_t *d_data;
    size_t m_size;
    // 这两个字段仅在 mapped 模式下生效；默认非 mapped。
    //
    // 非 mapped 模式（m_isMapped == false，默认）：
    //   d_data 是 cudaMalloc 分配的纯显存，h_data == nullptr。
    //   GPU 读写都在 VRAM 内，与 host 内存无关，无任何映射。
    //   绝大多数 Vector<bool>（含所有大层向量）都是此模式。
    //
    // mapped 模式（m_isMapped == true，仅 allocateMapped() 进入；全项目仅
    //   prospectiveBehaviorBuffer 一处，256 位）：
    //   h_data 是 host pinned 物理内存，d_data 经 cudaHostGetDevicePointer 取得，
    //   指向【同一块物理 RAM】（不是显存里的另一份拷贝，不是两份）。
    //   GPU 经 d_data 写 = 跨 PCIe 直接写 host RAM（zero-copy，无 D2H 传输）；
    //   host 经 h_data 读 = 读同一块 RAM。代价是 GPU 写跨 PCIe 比写 VRAM 慢，
    //   故仅适合极小、低频的缓冲，绝不可用于大层向量。
    uint32_t *h_data;   // 非 mapped 时恒为 nullptr
    bool m_isMapped;    // 默认 false；仅 allocateMapped() 置 true

    static void checkCudaError(cudaError_t err, const char *msg)
    {
        if (err != cudaSuccess)
        {
            throw std::runtime_error(std::string(msg) + ": " + cudaGetErrorString(err));
        }
    }

    void release() noexcept
    {
        if (m_isMapped)
        {
            // mapped 模式：d_data 是 h_data 的 device 视图，只需 cudaFreeHost(h_data)。
            if (h_data != nullptr)
            {
                cudaFreeHost(h_data);
                h_data = nullptr;
            }
            d_data = nullptr;
            m_isMapped = false;
        }
        else if (d_data != nullptr)
        {
            cudaFree(d_data);
            d_data = nullptr;
        }
        m_size = 0;
    }

    static size_t getWordCount(size_t size)
    {
        return (size + 31) / 32;
    }

public:
    Vector() : d_data(nullptr), m_size(0), h_data(nullptr), m_isMapped(false) {}

    explicit Vector(size_t size) : d_data(nullptr), m_size(size), h_data(nullptr), m_isMapped(false)
    {
        if (m_size > 0)
        {
            cudaError_t err = cudaMalloc(reinterpret_cast<void **>(&d_data), bytes());
            checkCudaError(err, "Failed to allocate device memory for bool vector (bit-packed)");
            cudaMemset(d_data, 0, bytes());
        }
    }

    ~Vector() { release(); }

    Vector(const Vector &) = delete;
    Vector &operator=(const Vector &) = delete;

    Vector(Vector &&other) noexcept
        : d_data(other.d_data), m_size(other.m_size), h_data(other.h_data), m_isMapped(other.m_isMapped)
    {
        other.d_data = nullptr;
        other.m_size = 0;
        other.h_data = nullptr;
        other.m_isMapped = false;
    }

    Vector &operator=(Vector &&other) noexcept
    {
        if (this != &other)
        {
            release();
            d_data = other.d_data;
            m_size = other.m_size;
            h_data = other.h_data;
            m_isMapped = other.m_isMapped;
            other.d_data = nullptr;
            other.m_size = 0;
            other.h_data = nullptr;
            other.m_isMapped = false;
        }
        return *this;
    }

    void allocate(size_t new_size)
    {
        if (m_size == new_size) return;

        release();
        m_size = new_size;
        if (m_size > 0)
        {
            cudaError_t err = cudaMalloc(reinterpret_cast<void **>(&d_data), bytes());
            checkCudaError(err, "Failed to allocate device memory for bool vector (bit-packed) in allocate()");
            cudaMemset(d_data, 0, bytes());
        }
    }

    // 分配为 host mapped pinned memory（cudaHostAllocMapped）。
    // d_data 经 cudaHostGetDevicePointer 取得，是 h_data 的 device 视图；
    // GPU 经 d_data 写等价于写 host 内存，host 经 hostData() 直接读，零 D2H、零 sync。
    // 设计原则（真善美第 3 条）：把“behavior 是否写完”这个不实在状态，
    // 用 host 直接可见的 mapped 内存固化，CPU 侧读取无需任何 CUDA 同步调用（不 flush WDDM 命令缓冲）。
    void allocateMapped(size_t new_size)
    {
        release();
        m_size = new_size;
        m_isMapped = true;
        if (m_size > 0)
        {
            cudaError_t err = cudaHostAlloc(reinterpret_cast<void **>(&h_data), bytes(), cudaHostAllocMapped);
            checkCudaError(err, "Failed to allocate mapped host memory for bool vector (bit-packed)");
            std::memset(h_data, 0, bytes());
            err = cudaHostGetDevicePointer(reinterpret_cast<void **>(&d_data), h_data, 0);
            checkCudaError(err, "Failed to get device pointer for mapped bool vector");
        }
    }

    // 返回 host 侧可读指针（仅 mapped 模式有效；非 mapped 返回 nullptr）。
    uint32_t *hostData() { return h_data; }
    const uint32_t *hostData() const { return h_data; }
    bool isMapped() const { return m_isMapped; }

    uint32_t *data() { return d_data; }
    const uint32_t *data() const { return d_data; }
    size_t size() const { return m_size; }
    size_t wordCount() const { return getWordCount(m_size); }
    size_t bytes() const { return wordCount() * sizeof(uint32_t); }

    void copyFromHost(const uint32_t *h_data, size_t wordCount, cudaStream_t stream = 0)
    {
        if (wordCount > this->wordCount())
            throw std::out_of_range("Word count exceeds vector capacity");
        if (wordCount == 0)
            return;
        if (h_data == nullptr)
            throw std::invalid_argument("Host data pointer must not be null");

        cudaError_t err = cudaMemcpyAsync(d_data, h_data, wordCount * sizeof(uint32_t), cudaMemcpyHostToDevice, stream);
        checkCudaError(err, "Failed to copy raw bit-packed data from host to device (Vector<bool>)");
    }

    void copyToHost(uint32_t *h_data, size_t wordCount) const
    {
        if (wordCount > this->wordCount())
            throw std::out_of_range("Word count exceeds vector capacity");
        if (wordCount == 0)
            return;
        if (h_data == nullptr)
            throw std::invalid_argument("Host data pointer must not be null");

        cudaError_t err = cudaMemcpy(h_data, d_data, wordCount * sizeof(uint32_t), cudaMemcpyDeviceToHost);
        checkCudaError(err, "Failed to copy raw bit-packed data from device to host (Vector<bool>)");
    }

    // 指定 stream 上的同步 D2H：cudaMemcpyAsync(D2H, stream) + cudaStreamSynchronize(stream)。
    // 单流同步（只等该 stream 的先前工作），不 drain 其它流（如 GL 渲染流）。
    // 供效应器在专用工作线程把 prospectiveBehaviorBuffer 读出到 host，避免 NULL 流的设备级同步。
    // 显式 sync 保证 D2H 写完后再返回，规避 WDDM 非 pinned async D2H 的释放时序歧义。
    // 注意：stream=0（NULL 流）时退化为设备级同步（等价于上面的同步版本），调用方应传非零流。
    void copyToHost(uint32_t *h_data, size_t wordCount, cudaStream_t stream) const
    {
        if (wordCount > this->wordCount())
            throw std::out_of_range("Word count exceeds vector capacity");
        if (wordCount == 0)
            return;
        if (h_data == nullptr)
            throw std::invalid_argument("Host data pointer must not be null");

        cudaError_t err = cudaMemcpyAsync(h_data, d_data, wordCount * sizeof(uint32_t), cudaMemcpyDeviceToHost, stream);
        checkCudaError(err, "Failed to copy raw bit-packed data from device to host async (Vector<bool>)");

        err = cudaStreamSynchronize(stream);
        checkCudaError(err, "Failed to synchronize stream after async D2H (Vector<bool>)");
    }

    void save(const std::filesystem::path &filename) const
    {
        std::ofstream ofs(filename, std::ios::binary);
        if (!ofs)
            throw std::runtime_error("Failed to open file for saving: " + filename.u8string());

        ofs.write(reinterpret_cast<const char *>(&m_size), sizeof(m_size));

        if (m_size > 0)
        {
            std::vector<uint32_t> h_data(wordCount());
            copyToHost(h_data.data(), wordCount());
            ofs.write(reinterpret_cast<const char *>(h_data.data()), bytes());
        }
    }

    void loadFromFile(const std::filesystem::path &filename)
    {
        std::ifstream ifs(filename, std::ios::binary);
        if (!ifs)
            throw std::runtime_error("Failed to open file for loading: " + filename.u8string());

        size_t saved_size;
        ifs.read(reinterpret_cast<char *>(&saved_size), sizeof(saved_size));

        allocate(saved_size);

        if (saved_size > 0)
        {
            std::vector<uint32_t> h_data(wordCount());
            ifs.read(reinterpret_cast<char *>(h_data.data()), bytes());
            if (!ifs)
                throw std::runtime_error("Error reading bit-packed data from file: " + filename.u8string());
            copyFromHost(h_data.data(), wordCount());
        }
    }

    void copyRegionFrom(size_t dest_offset_bits, const Vector<bool>& source, size_t src_offset_bits, size_t num_bits, cudaStream_t stream = 0)
    {
        if (num_bits == 0) return;

        if (dest_offset_bits + num_bits > this->m_size)
        {
            throw std::out_of_range("copyRegionFrom would write out of bounds of the destination vector.");
        }
        if (src_offset_bits + num_bits > source.size())
        {
            throw std::out_of_range("copyRegionFrom would read out of bounds of the source vector.");
        }

        stm_ai::core::utils::CudaBitCopy(
            this->d_data,
            this->size(),
            dest_offset_bits,
            source.data(),
            source.size(),
            src_offset_bits,
            num_bits,
            stream
        );
    }

    void setRegion(size_t dest_offset_bits, const Vector<bool>& source, cudaStream_t stream = 0)
    {
        if (source.size() == 0) return;

        this->copyRegionFrom(
            dest_offset_bits,
            source,
            0,
            source.size(),
            stream
        );
    }

    void copyRegionFromHost(size_t dest_offset_bits, const unsigned char* src_host_data, size_t num_bits, cudaStream_t stream = 0)
    {
        if (num_bits == 0) return;

        if (dest_offset_bits + num_bits > this->m_size)
        {
            throw std::out_of_range("copyRegionFromHost would write out of bounds of the destination vector.");
        }

        stm_ai::core::utils::CudaBitCopyFromHost(
            this->d_data,
            this->size(),
            dest_offset_bits,
            src_host_data,
            num_bits,
            stream
        );
    }

    __device__ static inline bool getBit(const uint32_t *data, size_t index)
    {
        return (data[index >> 5] >> (index & 31)) & 1u;
    }

    __device__ static inline void setBitAtomic(uint32_t *data, size_t index, bool val)
    {
        uint32_t mask = (1u << (index & 31));
        if (val)
            atomicOr(&data[index >> 5], mask);
        else
            atomicAnd(&data[index >> 5], ~mask);
    }

    __device__ static inline void setBitUnsafe(uint32_t *data, size_t index, bool val)
    {
        uint32_t mask = (1u << (index & 31));
        if (val)
            data[index >> 5] |= mask;
        else
            data[index >> 5] &= ~mask;
    }
};