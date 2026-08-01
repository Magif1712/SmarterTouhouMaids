#include "core/containers/vector/Vector.h"
#include <iostream>
#include <stdexcept>
#include "../gradient_ops.h"
#include "gradient_ops_bridge.h"


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
    )
    {
        if (!da0 || !da1 || !fz || !b || !p || !q || !l || !r || !dz_workspace)
        {
            throw std::invalid_argument("Received null pointer in backwardLayerBridge.");
        }

        try
        {
            cudaError_t err = backwardLayer(
                da0->data(),
                da1->data(),
                fz->data(),
                b->data(),
                p->data(),
                q->data(),
                l->data(),
                r->data(),
                dz_workspace->data(),
                batch_size,
                n_curr,
                n_prev,
                stream
            );
            if (err != cudaSuccess) {
                throw std::runtime_error(std::string("CUDA error in backwardLayerBridge: ") + cudaGetErrorString(err));
            }
        }
        catch (const std::exception& e)
        {
            throw;
        }
    }

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
    )
    {
        if (!da0 || !da1 || !a_prev || !fz || !b || !p || !q || !l || !r || !dz_workspace)
        {
            throw std::invalid_argument("Received null pointer in backwardGradientDescentLayerBridge.");
        }

        try
        {
            cudaError_t err = backwardGradientDescentLayer(
                da0->data(),
                da1->data(),
                a_prev->data(),
                fz->data(),
                b->data(),
                p->data(),
                q->data(),
                l->data(),
                r->data(),
                dz_workspace->data(),
                n_curr,
                n_prev,
                stream
            );
            if (err != cudaSuccess) {
                throw std::runtime_error(std::string("CUDA error in backwardGradientDescentLayerBridge: ") + cudaGetErrorString(err));
            }
        }
        catch (const std::exception& e)
        {
            throw;
        }
    }
}