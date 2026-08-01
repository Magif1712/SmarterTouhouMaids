#pragma once

#include <cuda_runtime.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 创建一个 CUDA Event。
 * Event 用于跨流的 GPU 侧同步：在 stream A 上 record，在 stream B 上 waitEvent，
 * 则 B 上后续操作会等 A 上 record 之前的操作完成，CPU 不阻塞。
 */
cudaEvent_t createEventBridge();

/**
 * 在指定 stream 上记录 event。
 * record 之后，event 与该 stream 上已提交的操作绑定。
 */
void recordEventBridge(cudaEvent_t event, cudaStream_t stream);

/**
 * 销毁 event，释放资源。
 */
void destroyEventBridge(cudaEvent_t event);

#ifdef __cplusplus
}
#endif
