#include "stream_ops_bridge.h"
#include <iostream>

extern "C" {

    cudaStream_t createStreamBridge() {
        cudaStream_t stream;
        cudaError_t err = cudaStreamCreate(&stream);
        if (err != cudaSuccess) {
            std::cerr << "CUDA error in createStreamBridge: " << cudaGetErrorString(err) << std::endl;
            return nullptr;
        }
        return stream;
    }

    void destroyStreamBridge(cudaStream_t stream) {
        if (stream != nullptr) {
            cudaError_t err = cudaStreamDestroy(stream);
            if (err != cudaSuccess) {
                std::cerr << "CUDA error in destroyStreamBridge: " << cudaGetErrorString(err) << std::endl;
            }
        }
    }

    void synchronizeStreamBridge(cudaStream_t stream) {
        if (stream != nullptr) {
            cudaError_t err = cudaStreamSynchronize(stream);
            if (err != cudaSuccess) {
                std::cerr << "CUDA error in synchronizeStreamBridge: " << cudaGetErrorString(err) << std::endl;
            }
        }
    }

    void streamWaitEventBridge(cudaStream_t stream, cudaEvent_t event) {
        if (stream != nullptr && event != nullptr) {
            cudaError_t err = cudaStreamWaitEvent(stream, event, 0);
            if (err != cudaSuccess) {
                std::cerr << "CUDA error in streamWaitEventBridge: " << cudaGetErrorString(err) << std::endl;
            }
        }
    }

}