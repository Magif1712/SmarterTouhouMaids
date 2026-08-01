#pragma once

#include <cuda_runtime.h>

#ifdef __cplusplus
extern "C" {
#endif

cudaStream_t createStreamBridge();
void destroyStreamBridge(cudaStream_t stream);
void synchronizeStreamBridge(cudaStream_t stream);
void streamWaitEventBridge(cudaStream_t stream, cudaEvent_t event);

#ifdef __cplusplus
}
#endif