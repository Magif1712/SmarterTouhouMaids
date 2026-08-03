#ifndef ELASTIC_OPS_BRIDGE_H
#define ELASTIC_OPS_BRIDGE_H

#include <cuda_runtime.h>
#include <cstdint>

template<typename T>
class Vector;

/**
 * @brief reconnectOnInputChange 的桥接函数。
 *
 * @param currentInput 当前输入 Vector<bool>
 * @param prevInput    上一步输入 Vector<bool>（调用后更新为 currentInput 的副本）
 * @param q            q 权重 Vector<bool>（原地修改）
 * @param stream       CUDA 流
 */
void ElasticReconnectOnInputChange(Vector<bool>* currentInput, Vector<bool>* prevInput,
                                   Vector<bool>* q, cudaStream_t stream);

#endif // ELASTIC_OPS_BRIDGE_H
