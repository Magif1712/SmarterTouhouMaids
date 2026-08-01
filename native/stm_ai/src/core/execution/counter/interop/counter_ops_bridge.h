#pragma once
#include <cstddef>
#include <cstdint>
#include <cuda_runtime.h>

// 前向声明，避免在头文件暴露实现细节
struct MappedCounter;

extern "C"
{
    // 创建一个 host mapped pinned uint32 计数器（generation）。
    // h_ptr 供 host 直接读，d_ptr 供 GPU kernel 经 atomicAdd_system 递增。
    // 失败返回 nullptr。
    MappedCounter *CounterCreate();

    // 销毁计数器并释放 pinned host 内存。
    void CounterDestroy(MappedCounter *counter);

    // 在 stream 上入队一个极小 kernel（1 thread）递增 generation。
    // 流内有序：调用方保证此调用排在 behavior 写入之后，故 host 看到 generation 变化
    // 即意味着此前的 behavior 写入已执行完毕。
    // atomicAdd_system 自带 system-scope 可见性，host 侧普通 load 即可读到新值。
    void CounterIncrement(MappedCounter *counter, cudaStream_t stream);

    // 纯 host 读：返回当前 generation 值。零 CUDA 调用，不 flush WDDM 命令缓冲。
    uint32_t CounterGetHostValue(MappedCounter *counter);
}
