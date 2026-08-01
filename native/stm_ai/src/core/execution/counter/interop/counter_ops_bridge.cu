#include <iostream>
#include <stdexcept>
#include <cstring>
#include <cuda_runtime.h>
#include "counter_ops_bridge.h"

// 设计说明（真善美第 3 条）：
// "behavior 是否写完"是一个不实在的状态。这里用一个 host 直接可见的 generation 计数器
// 把它固化为实在的整数：GPU 在 uranaStream 上（排在 extractBehaviorTo 之后）递增它，
// host 侧用普通 load 读取它。host 看到 generation 变化 = GPU 已执行到递增点 =
// 此前的 behavior 写入已完成 = mapped buffer 内容完整可读。
//
// 整条读取链路零 CUDA 同步调用（无 cudaStreamSynchronize / cudaEventQuery），
// 不 flush WDDM 命令缓冲，不打乱 uranaStream 的批处理节奏。
//
// 异常策略与其它 bridge 一致：捕获后 cerr 打印再重抛，让 JNI 层翻译为 Java RuntimeException。

struct MappedCounter
{
    uint32_t *h_ptr; // host 视图（pinned，可直接读）
    uint32_t *d_ptr; // device 视图（GPU 经此递增，等价于写 host 内存）
};

// system-scope 原子递增。atomicAdd_system 保证写入对 host 可见，无需额外 __threadfence_system。
__global__ void incrementGenerationKernel(unsigned int *d_ptr)
{
    atomicAdd_system(d_ptr, 1u);
}

extern "C"
{

MappedCounter *CounterCreate()
{
    try
    {
        auto *counter = new MappedCounter();
        counter->h_ptr = nullptr;
        counter->d_ptr = nullptr;

        cudaError_t err = cudaHostAlloc(reinterpret_cast<void **>(&counter->h_ptr),
                                        sizeof(uint32_t), cudaHostAllocMapped);
        if (err != cudaSuccess)
        {
            delete counter;
            std::cerr << "Error in CounterCreate (cudaHostAlloc): " << cudaGetErrorString(err) << std::endl;
            return nullptr;
        }
        *counter->h_ptr = 0u;

        err = cudaHostGetDevicePointer(reinterpret_cast<void **>(&counter->d_ptr),
                                       counter->h_ptr, 0);
        if (err != cudaSuccess)
        {
            cudaFreeHost(counter->h_ptr);
            delete counter;
            std::cerr << "Error in CounterCreate (cudaHostGetDevicePointer): " << cudaGetErrorString(err) << std::endl;
            return nullptr;
        }
        return counter;
    }
    catch (const std::exception &e)
    {
        std::cerr << "Error in CounterCreate: " << e.what() << std::endl;
        return nullptr;
    }
}

void CounterDestroy(MappedCounter *counter)
{
    if (counter)
    {
        if (counter->h_ptr)
            cudaFreeHost(counter->h_ptr);
        delete counter;
    }
}

void CounterIncrement(MappedCounter *counter, cudaStream_t stream)
{
    if (!counter || !counter->d_ptr)
        return;
    try
    {
        incrementGenerationKernel<<<1, 1, 0, stream>>>(counter->d_ptr);
        cudaError_t err = cudaGetLastError();
        if (err != cudaSuccess)
            throw std::runtime_error(std::string("Failed to launch increment kernel: ") + cudaGetErrorString(err));
    }
    catch (const std::exception &e)
    {
        std::cerr << "Error in CounterIncrement: " << e.what() << std::endl;
        throw;
    }
}

uint32_t CounterGetHostValue(MappedCounter *counter)
{
    if (!counter || !counter->h_ptr)
        return 0u;
    // 纯 host 读。atomicAdd_system 已保证 GPU 写入对 host 可见，无需任何 CUDA 调用。
    return *counter->h_ptr;
}

} // extern "C"
