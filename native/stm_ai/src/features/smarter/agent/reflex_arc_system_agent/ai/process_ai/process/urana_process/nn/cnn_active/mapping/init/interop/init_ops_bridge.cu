#include "init_ops_bridge.h"
#include "../init_ops.h"
#include "core/containers/vector/Vector.h"

// 重要：Java 传入的句柄是 Vector<float> 的「对象指针」，不是 CUDA 设备数据指针。
// 必须调用 ->data() 取出 d_data（设备指针）再传给内核，绝不能把对象指针直接 cast 成 float*。
//
// 同步语义：构造期一次性初始化（非热路径），launch 后同步该 stream，保证
// CnnActiveNeuralNetwork 构造返回时对称权重已写完，Urana 工作线程首轮 forward 前就绪。
void cnn_active_fill_symmetric_bridge(
    float bound, intptr_t seed, intptr_t stream /* -> */, intptr_t v)
{
    Vector<float> *vec = reinterpret_cast<Vector<float>*>(v);

    cnn_active_fill_symmetric(
        (int)vec->size(), bound, (uint64_t)seed, (cudaStream_t)stream /* -> */, vec->data());

    cudaStreamSynchronize((cudaStream_t)stream);
}
