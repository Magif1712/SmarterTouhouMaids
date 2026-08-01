#ifndef BNN_OPS_H
#define BNN_OPS_H

#include <cuda_runtime.h>
#include <stdint.h>

/**
 * @brief Negates and binarizes an integer array, storing the result as a bit-packed boolean array
 *        using a high-performance warp-level ballot implementation.
 *
 *        The logical operation is: dst[i] = (src[i] < 0)
 *
 * @param[in]  src        Input, integer array.
 * @param[out] dst_packed Output, bit-packed boolean array (uint32_t pointer).
 * @param[in]  n          Number of elements in the array.
 * @param[in]  stream     CUDA stream for asynchronous execution.
 * @return cudaError_t     Returns cudaSuccess on success, or an error code on failure.
 */
cudaError_t negateAndBinarize(
    uint32_t*       __restrict__ dst_packed,
    const int*      __restrict__ src,
    size_t          n,
    cudaStream_t    stream = 0
);

/**
 * @brief Negates and binarizes a sub-region of an integer array, storing the result as a bit-packed boolean array.
 *        This function guarantees that only the bits within the destination range are modified.
 *
 * @param[out] dst_packed        Output, bit-packed boolean array (uint32_t pointer).
 * @param[in]  dst_bit_offset    Starting bit offset in the destination array.
 * @param[in]  src               Input, integer array.
 * @param[in]  src_offset        Starting element offset in the source array.
 * @param[in]  n                 Number of elements to process.
 * @param[in]  stream            CUDA stream for asynchronous execution.
 * @return cudaError_t           Returns cudaSuccess on success, or an error code on failure.
 */
cudaError_t negateAndBinarizeRegion(
    uint32_t*       __restrict__ dst_packed,
    size_t          dst_bit_offset,
    const int*      __restrict__ src,
    size_t          src_offset,
    size_t          n,
    cudaStream_t    stream = 0
);

#endif // BNN_OPS_H