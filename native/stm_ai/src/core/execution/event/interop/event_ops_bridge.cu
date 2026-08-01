#include "event_ops_bridge.h"
#include <iostream>

extern "C" {

    cudaEvent_t createEventBridge() {
        cudaEvent_t event;
        cudaError_t err = cudaEventCreate(&event);
        if (err != cudaSuccess) {
            std::cerr << "CUDA error in createEventBridge: " << cudaGetErrorString(err) << std::endl;
            return nullptr;
        }
        return event;
    }

    void recordEventBridge(cudaEvent_t event, cudaStream_t stream) {
        if (event != nullptr) {
            // stream 可为 0（NULL 流），cudaEventRecord 支持
            cudaError_t err = cudaEventRecord(event, stream);
            if (err != cudaSuccess) {
                std::cerr << "CUDA error in recordEventBridge: " << cudaGetErrorString(err) << std::endl;
            }
        }
    }

    void destroyEventBridge(cudaEvent_t event) {
        if (event != nullptr) {
            cudaError_t err = cudaEventDestroy(event);
            if (err != cudaSuccess) {
                std::cerr << "CUDA error in destroyEventBridge: " << cudaGetErrorString(err) << std::endl;
            }
        }
    }

}
