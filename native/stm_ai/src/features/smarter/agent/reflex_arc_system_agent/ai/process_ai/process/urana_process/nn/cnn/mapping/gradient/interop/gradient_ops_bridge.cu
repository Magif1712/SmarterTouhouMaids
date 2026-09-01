#include "gradient_ops_bridge.h"
#include "../gradient_ops.h"
#include "core/containers/vector/Vector.h"

// buf_* 句柄始终有效（GradCellOp 两阶段都更新权重）。
// bufTc 为 0 时传 nullptr（跳过外拷输入梯度）。
void cnn_backward_layer_bridge(
    intptr_t traceZ, intptr_t traceY, intptr_t target, intptr_t x,
    intptr_t hp_p, intptr_t hp_q, intptr_t hp_l, intptr_t hp_r, intptr_t hp_b,
    intptr_t hp_idx0, intptr_t hp_idx1, intptr_t hp_w0, intptr_t hp_w1,
    int sizeA0, int sizeA1, int sizeC, float lr, intptr_t stream /* -> */,
    intptr_t buf_p, intptr_t buf_q, intptr_t buf_l, intptr_t buf_r, intptr_t buf_b,
    intptr_t buf_idx0, intptr_t buf_idx1, intptr_t buf_w0, intptr_t buf_w1,
    intptr_t dz, intptr_t dInput, intptr_t bufTc)
{
    Vector<float> *trace_z_vec = reinterpret_cast<Vector<float>*>(traceZ);
    Vector<float> *trace_y_vec = reinterpret_cast<Vector<float>*>(traceY);
    Vector<float> *target_vec  = reinterpret_cast<Vector<float>*>(target);
    Vector<float> *x_vec       = reinterpret_cast<Vector<float>*>(x);
    Vector<float> *hp_p_vec    = reinterpret_cast<Vector<float>*>(hp_p);
    Vector<float> *hp_q_vec    = reinterpret_cast<Vector<float>*>(hp_q);
    Vector<float> *hp_l_vec    = reinterpret_cast<Vector<float>*>(hp_l);
    Vector<float> *hp_r_vec    = reinterpret_cast<Vector<float>*>(hp_r);
    Vector<float> *hp_b_vec    = reinterpret_cast<Vector<float>*>(hp_b);
    Vector<int>   *hp_idx0_vec = reinterpret_cast<Vector<int>*>(hp_idx0);
    Vector<int>   *hp_idx1_vec = reinterpret_cast<Vector<int>*>(hp_idx1);
    Vector<float> *hp_w0_vec   = reinterpret_cast<Vector<float>*>(hp_w0);
    Vector<float> *hp_w1_vec   = reinterpret_cast<Vector<float>*>(hp_w1);

    Vector<float> *dz_vec     = reinterpret_cast<Vector<float>*>(dz);
    Vector<float> *dInput_vec = reinterpret_cast<Vector<float>*>(dInput);

    float *bufTc_ptr = (bufTc != 0) ? reinterpret_cast<Vector<float>*>(bufTc)->data() : nullptr;

    cnn_backward_layer(
        trace_z_vec->data(), trace_y_vec->data(), target_vec->data(), x_vec->data(),
        hp_p_vec->data(), hp_q_vec->data(), hp_l_vec->data(), hp_r_vec->data(), hp_b_vec->data(),
        hp_idx0_vec->data(), hp_idx1_vec->data(), hp_w0_vec->data(), hp_w1_vec->data(),
        sizeA0, sizeA1, sizeC, lr, (cudaStream_t)stream /* -> */,
        reinterpret_cast<Vector<float>*>(buf_p)->data(),
        reinterpret_cast<Vector<float>*>(buf_q)->data(),
        reinterpret_cast<Vector<float>*>(buf_l)->data(),
        reinterpret_cast<Vector<float>*>(buf_r)->data(),
        reinterpret_cast<Vector<float>*>(buf_b)->data(),
        reinterpret_cast<Vector<int>*>(buf_idx0)->data(),
        reinterpret_cast<Vector<int>*>(buf_idx1)->data(),
        reinterpret_cast<Vector<float>*>(buf_w0)->data(),
        reinterpret_cast<Vector<float>*>(buf_w1)->data(),
        dz_vec->data(), dInput_vec->data(), bufTc_ptr
    );

    // 不 cudaStreamSynchronize：热路径
}
