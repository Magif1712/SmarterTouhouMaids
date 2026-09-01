#include "bnn_ops_bridge.h"
#include "../bnn_ops.h"
#include "core/containers/vector/Vector.cu"
#include <stdexcept>
#include <iostream>

void negateAndBinarizeBridge_original(Vector<bool>* dst, const Vector<int>* src, cudaStream_t stream)
{
    if (!dst || !src) {
        throw std::invalid_argument("Received null pointer in negateAndBinarizeBridge_original.");
    }
    if (dst->size() != src->size()) {
        throw std::invalid_argument("Destination and source vectors must have the same size.");
    }

    try
    {
        cudaError_t err = negateAndBinarize_original(
            dst->data(),
            src->data(),
            src->size(),
            stream
        );

        if (err != cudaSuccess) {
            throw std::runtime_error(std::string("CUDA error in negateAndBinarize_original: ") + cudaGetErrorString(err));
        }
    }
    catch (const std::exception& e)
    {
        // 重新抛出异常，以便上层（JNI）可以捕获它
        throw;
    }
}

void negateAndBinarizeRegionBridge_original(
    Vector<bool>*      dst,
    size_t             dst_offset,
    const Vector<int>* src,
    size_t             src_offset,
    size_t             n,
    cudaStream_t       stream)
{
    if (!dst || !src) {
        throw std::invalid_argument("Received null pointer in negateAndBinarizeRegionBridge_original.");
    }
    if (src_offset + n > src->size()) {
        throw std::out_of_range("Source range is out of bounds.");
    }
    if (dst_offset + n > dst->size()) {
        throw std::out_of_range("Destination range is out of bounds.");
    }

    try
    {
        cudaError_t err = negateAndBinarizeRegion_original(
            dst->data(),
            dst_offset,
            src->data(),
            src_offset,
            n,
            stream
        );

        if (err != cudaSuccess) {
            throw std::runtime_error(std::string("CUDA error in negateAndBinarizeRegion_original: ") + cudaGetErrorString(err));
        }
    }
    catch (const std::exception& e)
    {
        // 重新抛出异常，以便上层（JNI）可以捕获它
        throw;
    }
}