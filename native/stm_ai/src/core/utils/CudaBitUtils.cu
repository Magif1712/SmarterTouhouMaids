#include "CudaBitUtils.h"
#include <stdexcept>
#include <algorithm> // for std::min, std::max
#include <vector>

// ------------------------------------------------------------
// 内部实现细节 (Internal Implementation Details)
// ------------------------------------------------------------
namespace
{ // 使用匿名命名空间封装实现细节

    using bitmask_t = uint32_t;
    static constexpr uint32_t BITS_PER_WORD = 32;
    static constexpr uint32_t LOG_WARP_SIZE = 5; // log2(32)

    // ------------------------------------------------------------
    // 设备函数: 从源位数组的任意位偏移处读取一个 32-bit 字
    // ------------------------------------------------------------
    __device__ __forceinline__ bitmask_t load_bits_from_offset(
        const bitmask_t *__restrict__ src,
        size_t bit_offset,
        size_t src_total_words // 传入源的总字数以进行边界检查
    )
    {
        size_t word_idx = bit_offset >> LOG_WARP_SIZE;
        uint32_t shift = bit_offset & (BITS_PER_WORD - 1);

        bitmask_t lo = __ldg(&src[word_idx]);
        // 安全读取: 只有当 word_idx + 1 在源数组界内时才读取 hi，否则为 0
        bitmask_t hi = (word_idx + 1 < src_total_words) ? __ldg(&src[word_idx + 1]) : 0;

        return __funnelshift_r(lo, hi, shift);
    }

    // ------------------------------------------------------------
    // 核心 Kernel: 将 src 的内容复制到 dst 的指定偏移处
    // ------------------------------------------------------------
    __global__ void bit_copy_kernel(
        bitmask_t *__restrict__ dst,
        size_t dst_offset_bits,
        const bitmask_t *__restrict__ src,
        size_t src_offset_bits,
        size_t num_bits,
        size_t src_total_words // 为安全边界检查而添加
    )
    {
        size_t tid = blockIdx.x * blockDim.x + threadIdx.x;
        size_t nthreads = gridDim.x * blockDim.x;

        size_t dst_start_word = dst_offset_bits >> LOG_WARP_SIZE;
        size_t dst_end_word = (dst_offset_bits + num_bits - 1) >> LOG_WARP_SIZE;
        size_t total_words = dst_end_word - dst_start_word + 1;

        for (size_t i = tid; i < total_words; i += nthreads)
        {
            size_t dst_word = dst_start_word + i;
            size_t word_start_bit = dst_word << LOG_WARP_SIZE;

            size_t copy_l = max(word_start_bit, dst_offset_bits);
            size_t copy_r = min(word_start_bit + BITS_PER_WORD, dst_offset_bits + num_bits);

            if (copy_l >= copy_r)
                continue;

            size_t src_bit = (copy_l - dst_offset_bits) + src_offset_bits;

            // 使用传入的 src_total_words 进行安全的边界检查
            bitmask_t val = load_bits_from_offset(src, src_bit, src_total_words);

            uint32_t n_bits = static_cast<uint32_t>(copy_r - copy_l);
            if (n_bits < BITS_PER_WORD)
            {
                bitmask_t mask = (1ull << n_bits) - 1;
                val &= static_cast<bitmask_t>(mask);
            }

            uint32_t in_word_shift = static_cast<uint32_t>(copy_l - word_start_bit);

            // Grid-stride 循环中的这种写入模式是安全的，因为每个线程在循环的不同迭代中处理不同的 `dst_word`。
            // 只有在多个块/线程可能同时写入 *同一个* `dst_word` 的情况下，才需要原子操作。
            // 优化：当整个 word 被覆盖时，直接写入，避免 RMW (Read-Modify-Write)
            if (n_bits == BITS_PER_WORD && in_word_shift == 0)
            {
                dst[dst_word] = val;
            }
            else
            {
                bitmask_t old_val = dst[dst_word];
                bitmask_t write_mask;
                if (n_bits == BITS_PER_WORD)
                {
                    write_mask = 0xFFFFFFFFu;
                }
                else
                {
                    write_mask = static_cast<bitmask_t>((1ull << n_bits) - 1) << in_word_shift;
                }

                bitmask_t new_val = (old_val & ~write_mask) | (val << in_word_shift);
                dst[dst_word] = new_val;
            }
        }
    }

    // ------------------------------------------------------------
    // Kernel: 从一个临时的 bit-packed 源解包并写入目标
    // ------------------------------------------------------------
    __global__ void bit_unpack_and_write_kernel(
        bitmask_t *dst, size_t dst_offset_bits, const bitmask_t *tmp_src, size_t num_bits)
    {
        for (size_t i = blockIdx.x * blockDim.x + threadIdx.x; i < num_bits; i += blockDim.x * gridDim.x)
        {
            // 1. 从临时源中读取第 i 个布尔值
            bool val = (tmp_src[i / BITS_PER_WORD] >> (i % BITS_PER_WORD)) & 1;

            // 2. 将这个布尔值原子地写入最终目标的正确位置
            size_t dst_bit = dst_offset_bits + i;
            size_t dst_word_idx = dst_bit / BITS_PER_WORD;
            uint32_t mask = 1u << (dst_bit % BITS_PER_WORD);

            if (val)
            {
                atomicOr(&dst[dst_word_idx], mask);
            }
            else
            {
                atomicAnd(&dst[dst_word_idx], ~mask);
            }
        }
    }

} // anonymous namespace

// ------------------------------------------------------------
// 公共接口实现 (Public Interface Implementation)
// ------------------------------------------------------------
namespace stm_ai
{
    namespace core
    {
        namespace utils
        {

            void CudaBitCopy(
                uint32_t *dst,
                size_t dst_size_bits,
                size_t dst_offset_bits,
                const uint32_t *src,
                size_t src_size_bits,
                size_t src_offset_bits,
                size_t num_bits,
                cudaStream_t stream)
            {
                if (!dst || !src)
                {
                    throw std::invalid_argument("CudaBitCopy: Destination and source pointers cannot be null.");
                }
                if (num_bits == 0)
                {
                    return;
                }

                // 边界检查
                if (dst_offset_bits + num_bits > dst_size_bits)
                {
                    throw std::invalid_argument("CudaBitCopy: Destination copy range exceeds bounds.");
                }
                if (src_offset_bits + num_bits > src_size_bits)
                {
                    throw std::invalid_argument("CudaBitCopy: Source copy range exceeds source bounds.");
                }

                // ========== 快速路径：完全对齐且满节 ==========
                const bool dst_aligned = (dst_offset_bits % BITS_PER_WORD == 0);
                const bool src_aligned = (src_offset_bits % BITS_PER_WORD == 0);
                const bool full_words = (num_bits % BITS_PER_WORD == 0);

                if (dst_aligned && src_aligned && full_words)
                {
                    size_t num_words = num_bits / BITS_PER_WORD;
                    size_t dst_word_offset = dst_offset_bits / BITS_PER_WORD;
                    size_t src_word_offset = src_offset_bits / BITS_PER_WORD;

                    cudaError_t err = cudaMemcpyAsync(
                        dst + dst_word_offset,
                        src + src_word_offset,
                        num_words * sizeof(uint32_t),
                        cudaMemcpyDeviceToDevice,
                        stream);

                    if (err != cudaSuccess)
                    {
                        // 在CUDA调用失败时提供更详细的错误信息
                        throw std::runtime_error(
                            std::string("CudaBitCopy: cudaMemcpyAsync failed with error: ") + cudaGetErrorString(err));
                    }
                    return;
                }

                // ========== 慢路径：通用 bit 级复制 ==========
                size_t src_total_words = (src_size_bits + BITS_PER_WORD - 1) / BITS_PER_WORD;

                size_t dst_start_word = dst_offset_bits >> LOG_WARP_SIZE;
                size_t dst_end_word = (dst_offset_bits + num_bits - 1) >> LOG_WARP_SIZE;
                size_t total_words = dst_end_word - dst_start_word + 1;

                int block_size = 256;
                int grid_size = static_cast<int>((total_words + block_size - 1) / block_size);
                grid_size = std::min(grid_size, 65535);
                grid_size = std::max(grid_size, 1);

                bit_copy_kernel<<<grid_size, block_size, 0, stream>>>(
                    dst,
                    dst_offset_bits,
                    src,
                    src_offset_bits,
                    num_bits,
                    src_total_words);
            }

            void CudaBitCopyFromHost(
                uint32_t *dst_device,
                size_t dst_size_bits,
                size_t dst_offset_bits,
                const unsigned char *src_host,
                size_t num_bits,
                cudaStream_t stream)
            {
                if (num_bits == 0)
                    return;

                // 边界检查
                if (dst_offset_bits + num_bits > dst_size_bits)
                {
                    throw std::invalid_argument("CudaBitCopyFromHost: Destination copy range exceeds bounds.");
                }
                if (!dst_device || !src_host)
                {
                    throw std::invalid_argument("CudaBitCopyFromHost: Destination and source pointers cannot be null.");
                }

                // 步骤 1: 在 CPU 上，将 jboolean[] (通常是 8-bit) 紧凑地打包成 uint32_t[]
                size_t num_words = (num_bits + 31) / 32;
                std::vector<uint32_t> packed_host_data(num_words, 0);
                for (size_t i = 0; i < num_bits; ++i)
                {
                    if (src_host[i])
                    {
                        packed_host_data[i / 32] |= (1u << (i % 32));
                    }
                }

                // ========== 新增快速路径：当目标对齐且长度为整字时 ==========
                if (dst_offset_bits % BITS_PER_WORD == 0 && num_bits % BITS_PER_WORD == 0)
                {
                    size_t dst_word_offset = dst_offset_bits / BITS_PER_WORD;
                    cudaError_t err = cudaMemcpyAsync(
                        dst_device + dst_word_offset,
                        packed_host_data.data(),
                        num_words * sizeof(uint32_t),
                        cudaMemcpyHostToDevice,
                        stream);
                    if (err != cudaSuccess)
                    {
                        throw std::runtime_error(
                            std::string("CudaBitCopyFromHost: cudaMemcpyAsync failed in fast path with error: ") +
                            cudaGetErrorString(err));
                    }
                    return; // 任务完成，无需临时 GPU buffer 和 unpack kernel
                }

                // ========== 慢路径：通用 bit 级解包写入 ==========
                // 步骤 2: 在 GPU 上分配一个临时的、足够大的缓冲区
                uint32_t *tmp_gpu_buffer;
                cudaError_t err = cudaMallocAsync(&tmp_gpu_buffer, num_words * sizeof(uint32_t), stream);
                if (err != cudaSuccess)
                {
                    throw std::runtime_error("Failed to allocate temporary GPU buffer in CudaBitCopyFromHost.");
                }

                // 步骤 3: 将 CPU 上打包好的数据，一次性异步复制到这个临时 GPU 缓冲区
                err = cudaMemcpyAsync(tmp_gpu_buffer, packed_host_data.data(), num_words * sizeof(uint32_t), cudaMemcpyHostToDevice, stream);
                if (err != cudaSuccess)
                {
                    cudaFreeAsync(tmp_gpu_buffer, stream);
                    throw std::runtime_error("Failed to copy packed data to temporary GPU buffer.");
                }

                // 步骤 4: 启动 Kernel，让 GPU 并行地从临时缓冲区读取数据，并写入最终的目标位置
                int threads_per_block = 256;
                int blocks_per_grid = (num_bits + threads_per_block - 1) / threads_per_block;
                blocks_per_grid = std::min(blocks_per_grid, 65535);
                blocks_per_grid = std::max(blocks_per_grid, 1);

                bit_unpack_and_write_kernel<<<blocks_per_grid, threads_per_block, 0, stream>>>(
                    dst_device, dst_offset_bits, tmp_gpu_buffer, num_bits);

                // 步骤 5: 异步释放临时 GPU 缓冲区
                cudaFreeAsync(tmp_gpu_buffer, stream);
            }

        } // namespace utils
    } // namespace core
} // namespace stm_ai