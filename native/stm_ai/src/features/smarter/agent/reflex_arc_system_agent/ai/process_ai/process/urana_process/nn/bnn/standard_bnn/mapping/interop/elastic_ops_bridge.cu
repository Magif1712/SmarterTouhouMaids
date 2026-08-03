#include "elastic_ops_bridge.h"
#include "../elastic_ops.h"
#include "core/containers/vector/Vector.cu"
#include <stdexcept>
#include <iostream>

void ElasticReconnectOnInputChange(Vector<bool>* currentInput, Vector<bool>* prevInput,
                                   Vector<bool>* q, cudaStream_t stream)
{
    if (!currentInput || !prevInput || !q) {
        throw std::invalid_argument("Received null pointer in ElasticReconnectOnInputChange.");
    }

    cudaError_t err = reconnectOnInputChange(
        q->data(),
        currentInput->data(),
        prevInput->data(),
        q->size(),
        q->wordCount(),
        stream);

    if (err != cudaSuccess) {
        throw std::runtime_error(std::string("reconnectOnInputChange failed: ") + cudaGetErrorString(err));
    }
}
