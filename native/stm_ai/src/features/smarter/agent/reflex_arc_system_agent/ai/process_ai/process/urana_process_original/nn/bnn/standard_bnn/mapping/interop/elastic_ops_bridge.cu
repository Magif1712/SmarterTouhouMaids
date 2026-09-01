#include "elastic_ops_bridge.h"
#include "../elastic_ops.h"
#include "core/containers/vector/Vector.cu"
#include <stdexcept>
#include <iostream>

void ElasticReconnectOnInputChange_original(Vector<bool>* currentInput, Vector<bool>* prevInput,
                                   Vector<bool>* q, cudaStream_t stream)
{
    if (!currentInput || !prevInput || !q) {
        throw std::invalid_argument("Received null pointer in ElasticReconnectOnInputChange_original.");
    }

    cudaError_t err = reconnectOnInputChange_original(
        q->data(),
        currentInput->data(),
        prevInput->data(),
        q->size(),
        q->wordCount(),
        stream);

    if (err != cudaSuccess) {
        throw std::runtime_error(std::string("reconnectOnInputChange_original failed: ") + cudaGetErrorString(err));
    }
}
