#include "elastic_ops.h"

__global__ void reconnectOnInputChangeKernel(
    uint32_t* __restrict__ q_data,
    const uint32_t* __restrict__ input_data,
    const uint32_t* __restrict__ prev_data,
    size_t num_words)
{
    size_t word_idx = blockIdx.x * blockDim.x + threadIdx.x;
    if (word_idx >= num_words) return;

    uint32_t changed = input_data[word_idx] ^ prev_data[word_idx];
    uint32_t current_q = q_data[word_idx];

    uint32_t reconnect_mask = changed & ~current_q;
    q_data[word_idx] = current_q | reconnect_mask;
}

__global__ void copyWordsKernel(
    uint32_t* __restrict__ dst,
    const uint32_t* __restrict__ src,
    size_t num_words)
{
    size_t word_idx = blockIdx.x * blockDim.x + threadIdx.x;
    if (word_idx >= num_words) return;
    dst[word_idx] = src[word_idx];
}

cudaError_t reconnectOnInputChange(
    uint32_t*       __restrict__ q_data,
    const uint32_t* __restrict__ input_data,
    uint32_t*       __restrict__ prev_data,
    size_t          num_bits,
    size_t          num_words,
    cudaStream_t    stream)
{
    if (num_words == 0 || q_data == nullptr || input_data == nullptr || prev_data == nullptr) {
        return cudaErrorInvalidValue;
    }

    constexpr int threads = 256;
    int blocks = static_cast<int>((num_words + threads - 1) / threads);

    reconnectOnInputChangeKernel<<<blocks, threads, 0, stream>>>(
        q_data, input_data, prev_data, num_words);

    cudaError_t err = cudaGetLastError();
    if (err != cudaSuccess) return err;

    copyWordsKernel<<<blocks, threads, 0, stream>>>(
        prev_data, input_data, num_words);

    return cudaGetLastError();
}
