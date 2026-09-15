#pragma once
#include <cstdint>

// CNN-Active 对称初始化 bridge（参数全用 intptr_t，Windows x64 LLP64 安全）。
//
// v 句柄是 Vector<float> 的「对象指针」，不是 CUDA 设备数据指针——bridge 内 ->data() 取 d_data。
// 同步语义：launch 后 cudaStreamSynchronize(stream)，保证构造期对称权重在工作线程启动前写完
// （一次性开销，非热路径）——与核心 VectorFillRandomFloat 的同步约定同构。
void cnn_active_fill_symmetric_bridge(
    float bound, intptr_t seed, intptr_t stream /* -> */, intptr_t v);
