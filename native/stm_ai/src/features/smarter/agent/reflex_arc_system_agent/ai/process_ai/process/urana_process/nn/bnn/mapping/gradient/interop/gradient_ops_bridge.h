#pragma once
#include <cuda_runtime.h>

// 前向声明，避免包含完整的 CUDA 头文件
template <typename T>
class Vector;

extern "C"
{
    void backwardLayerBridge(
        Vector<int>* da0,
        const Vector<int>* da1,
        const Vector<bool>* fz,
        const Vector<bool>* b,
        const Vector<int>* p,
        const Vector<bool>* q,
        const Vector<bool>* l,
        const Vector<bool>* r,
        Vector<int>* dz_workspace,
        int batch_size,
        int n_curr,
        int n_prev,
        cudaStream_t stream
    );

    void backwardGradientDescentLayerBridge(
        Vector<int>* da0,
        const Vector<int>* da1,
        const Vector<bool>* a_prev,
        const Vector<bool>* fz,
        Vector<bool>* b,
        Vector<int>* p,
        Vector<bool>* q,
        Vector<bool>* l,
        Vector<bool>* r,
        Vector<int>* dz_workspace,
        int n_curr,
        int n_prev,
        cudaStream_t stream
    );
}