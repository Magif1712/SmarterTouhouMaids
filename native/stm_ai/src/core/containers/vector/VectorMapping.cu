#include "VectorMapping.h"
#include <cuda_runtime.h>
#include <stdexcept>
#include <type_traits>

/**
 * @brief 将源位向量 A 中的每一位按照索引映射 P 分散写入目标位向量 B。
 * 每个线程处理一个输入元素 i。
 * 使用原子操作保证多个线程写入同一 32 位字时的正确性。
 */
__global__ void scatterBitsAtomicKernel(
    const uint32_t *__restrict__ A_bits,
    uint32_t *__restrict__ B_bits,
    const int *__restrict__ P,
    unsigned long long N)
{
    unsigned long long i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= N)
        return;

    // 1. 读取 A 的第 i 位
    bool val = Vector<bool>::getBit(A_bits, i);

    // 2. 计算目标位置
    int dst = P[i];
    if (dst < 0)
        return; // 负数索引视为无效或忽略

    // 3. 原子写入到 B
    Vector<bool>::setBitAtomic(B_bits, static_cast<size_t>(dst), val);
}

/**
 * @brief Host 包装函数：执行位散列操作。
 */
void scatterBits(const Vector<bool> &src, Vector<bool> &dst, const Vector<int> &P)
{
    if (src.size() == 0)
        return;

    unsigned long long N = src.size();

    // 线程块配置
    constexpr int threadsPerBlock = 256;
    int blocksPerGrid = static_cast<int>((N + threadsPerBlock - 1) / threadsPerBlock);

    // 清除残留的 CUDA 错误状态，避免上一次被吞掉的错误污染本次启动检查
    cudaGetLastError();

    scatterBitsAtomicKernel<<<blocksPerGrid, threadsPerBlock>>>(
        src.data(),
        dst.data(),
        P.data(),
        N);

    // 检查启动错误
    cudaError_t err = cudaGetLastError();
    if (err != cudaSuccess)
    {
        throw std::runtime_error(std::string("scatterBitsAtomicKernel launch failed: ") + cudaGetErrorString(err));
    }
}

/**
 * @brief 对两个位压缩向量进行 Word 级别的 XOR 操作 (A ^= B)。
 * 每个线程处理一个 32 位的字，比逐位操作快 32 倍。
 */
__global__ void xorWordsKernel(uint32_t *A, const uint32_t *B, size_t wordCount)
{
    size_t idx = blockIdx.x * blockDim.x + threadIdx.x;
    if (idx < wordCount)
    {
        A[idx] ^= B[idx];
    }
}

/**
 * @brief Host 包装函数：执行 A ^= B 操作。
 */
void xorVectors(Vector<bool> &A, const Vector<bool> &B)
{
    if (A.size() != B.size())
    {
        throw std::invalid_argument("Vector sizes must match for XOR operation");
    }

    size_t words = A.wordCount();
    if (words == 0)
        return;

    constexpr int threadsPerBlock = 256;
    int blocksPerGrid = static_cast<int>((words + threadsPerBlock - 1) / threadsPerBlock);

    // 清除残留的 CUDA 错误状态
    cudaGetLastError();

    xorWordsKernel<<<blocksPerGrid, threadsPerBlock>>>(A.data(), B.data(), words);

    cudaError_t err = cudaGetLastError();
    if (err != cudaSuccess)
    {
        throw std::runtime_error(std::string("xorWordsKernel launch failed: ") + cudaGetErrorString(err));
    }
}

/**
 * @brief [最优版] 从两个位压缩的布尔向量计算差值，并将结果存入一个整数向量。
 *        采用按 32 位字批量处理的方式，充分利用位压缩优势和内存合并访问，性能远超逐位处理。
 * @param result 目标整数向量，指针无别名
 * @param a      被减数布尔向量 (位压缩)，指针无别名
 * @param b      减数布尔向量 (位压缩)，指针无别名
 * @param n      元素总数 (比特数)
 */
__global__ void subtractBoolVectorsKernel( 
    const uint32_t* __restrict__ d_A, 
    const uint32_t* __restrict__ d_B, 
    int64_t                           bit_offset,    // A/B 共同起始 bit 
    int32_t* __restrict__           d_C, 
    int64_t                           c_int_offset,  // C 起始 int32 
    int64_t                           bit_length)    // 处理 bit 数 
 { 
    int64_t tid    = (int64_t)blockIdx.x * blockDim.x + threadIdx.x; 
    int64_t stride = (int64_t)gridDim.x * blockDim.x; 
 
 
    for (int64_t idx = tid; idx < bit_length; idx += stride) 
    { 
        int64_t bit = bit_offset + idx; 
 
 
        uint32_t a_word = __ldg(&d_A[bit >> 5]); 
        uint32_t b_word = __ldg(&d_B[bit >> 5]); 
 
 
        int a = (a_word >> (bit & 31)) & 1; 
        int b = (b_word >> (bit & 31)) & 1; 
 
 
        d_C[c_int_offset + idx] = a - b; 
    } 
 }

// 主机端封装 
 void subtractBoolVectors( 
     const uint32_t* d_A, 
     const uint32_t* d_B, 
     int64_t         bit_offset, 
     int32_t*        d_C, 
     int64_t         c_int_offset, 
     int64_t         bit_length, 
     cudaStream_t    stream) 
 { 
     if (bit_length <= 0) return; 
 
 
     constexpr int blockSize = 256; 
     int gridSize = (int)((bit_length + blockSize - 1) / blockSize); 
 
 
     // 限制 grid 规模，避免过度 launch（可选，根据 GPU 规模调整） 
     // int smCount; 
     // cudaDeviceGetAttribute(&smCount, cudaDevAttrMultiProcessorCount, 0); 
     // gridSize = min(gridSize, smCount * 4); 
 
 
     subtractBoolVectorsKernel<<<gridSize, blockSize, 0, stream>>>(
         d_A, d_B, bit_offset, d_C, c_int_offset, bit_length);

     // 清除残留错误，避免上一次被吞掉的错误污染本次启动检查
     cudaGetLastError() ; // intentionally clear
 }

/**
 * @brief [高性能] 对向量的指定区间执行原地标量乘法。
 *        vector[offset + i] *= scalar;  i ∈ [0, length)
 * @tparam T 向量元素的类型 (非 bool)
 * @param vector 要修改的向量，指针无别名
 * @param scalar 要乘的标量
 * @param offset 区间起始偏移（元素个数）
 * @param length 区间长度（元素个数）
 */
template <typename T>
__global__ void multiplyVectorByScalarInPlaceRangeKernel(
    T* __restrict__ vector,
    T scalar,
    size_t offset,
    size_t length)
{
    const size_t stride = static_cast<size_t>(gridDim.x) * blockDim.x;

    for (size_t i = static_cast<size_t>(blockIdx.x) * blockDim.x + threadIdx.x;
         i < length;
         i += stride)
    {
        vector[offset + i] *= scalar;
    }
}

/**
 * @brief Host 包装函数：对 Vector<T> 的指定区间执行原地标量乘法。
 * @param vector 要修改的 Vector
 * @param scalar 要乘的标量
 * @param offset 区间起始偏移（元素个数，非字节）
 * @param length 区间长度（元素个数）
 */
template <typename T>
void multiplyVectorByScalarInPlace(
    Vector<T>& vector,
    T scalar,
    size_t offset,
    size_t length,
    cudaStream_t stream)
{
    static_assert(!std::is_same<T, bool>::value,
                  "multiplyVectorByScalarInPlace cannot be used with bool.");

    if (length == 0) return;
    if (scalar == static_cast<T>(1)) return;

    // 边界检查
    if (offset + length > vector.size())
    {
        throw std::out_of_range("Interval [offset, offset+length) exceeds vector size");
    }

    // 检查设备指针有效性
    if (vector.data() == nullptr)
    {
        throw std::runtime_error("multiplyVectorByScalarInPlace: vector device pointer is null (allocation failed?)");
    }

    constexpr int threadsPerBlock = 256;
    int blocksPerGrid = static_cast<int>((length + threadsPerBlock - 1) / threadsPerBlock);

    // 清除残留的 CUDA 错误状态，避免上一次被吞掉的错误被误判为本次启动失败
    cudaGetLastError();

    multiplyVectorByScalarInPlaceRangeKernel<T><<<blocksPerGrid, threadsPerBlock, 0, stream>>>(
        vector.data(),
        scalar,
        offset,
        length
    );

    cudaError_t err = cudaGetLastError();
    if (err != cudaSuccess)
    {
        throw std::runtime_error(std::string("Range kernel launch failed: ") + cudaGetErrorString(err));
    }
}

// Explicit instantiations
template void multiplyVectorByScalarInPlace<int>(Vector<int>& vector, int scalar, size_t offset, size_t length, cudaStream_t stream);

// ====================================================================
// 随机初始化（PCG hash，无 curand 依赖）
// ====================================================================

// PCG hash：快速高质量哈希。把 (seed, 元素索引) 映射为伪随机 uint32_t。
// 选 PCG 而非 curand：无外部库依赖、kernel 自包含、并行度满，且初始化对随机质量要求不高。
__device__ __forceinline__ uint32_t pcgHash(uint32_t state)
{
    state = state * 747796405u + 2891336453u;
    uint32_t word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

// 每个 word（32 bits）填一个 PCG 随机值 → 32 个独立随机 bit。
__global__ void fillRandomBitsKernel(uint32_t *__restrict__ data, size_t num_words, uint32_t seed)
{
    size_t i = (size_t)blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= num_words)
        return;
    data[i] = pcgHash(seed ^ (uint32_t)i);
}

// 每个 int 填一个 [0, max_val) 的随机值。
__global__ void fillRandomIntsKernel(int32_t *__restrict__ data, size_t n, int32_t max_val, uint32_t seed)
{
    size_t i = (size_t)blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= n)
        return;
    uint32_t h = pcgHash(seed ^ (uint32_t)i);
    data[i] = (max_val > 0) ? (int32_t)(h % (uint32_t)max_val) : 0;
}

void fillRandomBits(Vector<bool> &vec, uint64_t seed, cudaStream_t stream)
{
    size_t words = vec.wordCount();
    if (words == 0)
        return;
    if (vec.data() == nullptr)
        throw std::runtime_error("fillRandomBits: vector device pointer is null (allocation failed?)");

    constexpr int threads = 256;
    int blocks = static_cast<int>((words + threads - 1) / threads);
    uint32_t s = (uint32_t)seed ^ (uint32_t)(seed >> 32);

    cudaGetLastError(); // 清除残留 sticky 错误，避免污染本次启动检查
    fillRandomBitsKernel<<<blocks, threads, 0, stream>>>(vec.data(), words, s);
    cudaError_t err = cudaGetLastError();
    if (err != cudaSuccess)
        throw std::runtime_error(std::string("fillRandomBitsKernel launch failed: ") + cudaGetErrorString(err));
}

void fillRandomInts(Vector<int> &vec, int maxVal, uint64_t seed, cudaStream_t stream)
{
    size_t n = vec.size();
    if (n == 0)
        return;
    if (vec.data() == nullptr)
        throw std::runtime_error("fillRandomInts: vector device pointer is null (allocation failed?)");

    constexpr int threads = 256;
    int blocks = static_cast<int>((n + threads - 1) / threads);
    uint32_t s = (uint32_t)seed ^ (uint32_t)(seed >> 32);

    cudaGetLastError();
    fillRandomIntsKernel<<<blocks, threads, 0, stream>>>(vec.data(), n, maxVal, s);
    cudaError_t err = cudaGetLastError();
    if (err != cudaSuccess)
        throw std::runtime_error(std::string("fillRandomIntsKernel launch failed: ") + cudaGetErrorString(err));
}