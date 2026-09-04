#include "inference_ops_bridge.h"
#include "../inference_ops.h"
#include "core/containers/vector/Vector.h"

// 重要：Java 传入的句柄是 Vector<float>/Vector<int> 的「对象指针」，
// 不是 CUDA 设备数据指针。必须调用 ->data() 取出 d_data（设备指针）再传给内核。
// 绝不能把对象指针直接 cast 成 float* —— 那会把 host 堆地址当 device 用。
void cnn_forward_layer_bridge(
    intptr_t x, intptr_t p, intptr_t q, intptr_t l, intptr_t r, intptr_t b,
    intptr_t idx0, intptr_t idx1, intptr_t w0, intptr_t w1,
    int sizeA0, int sizeA1, intptr_t stream /* -> */,
    intptr_t y, intptr_t traceZ, intptr_t traceY)
{
    Vector<float> *x_vec    = reinterpret_cast<Vector<float>*>(x);
    Vector<float> *q_vec    = reinterpret_cast<Vector<float>*>(q);
    Vector<float> *l_vec    = reinterpret_cast<Vector<float>*>(l);
    Vector<float> *r_vec    = reinterpret_cast<Vector<float>*>(r);
    Vector<float> *b_vec    = reinterpret_cast<Vector<float>*>(b);
    Vector<int>   *idx0_vec = reinterpret_cast<Vector<int>*>(idx0);
    Vector<int>   *idx1_vec = reinterpret_cast<Vector<int>*>(idx1);
    Vector<float> *w0_vec   = reinterpret_cast<Vector<float>*>(w0);
    Vector<float> *w1_vec   = reinterpret_cast<Vector<float>*>(w1);
    Vector<float> *y_vec    = reinterpret_cast<Vector<float>*>(y);

    // p 句柄接收但前向不用（p 仅 refreshCache/backward 用），不转发给 host。

    if (traceZ == 0) {
        // NoTrace: z 用 y 做工作区（push→pull→activate 覆盖 y）
        cnn_forward_layer_notrace(
            x_vec->data(), q_vec->data(), l_vec->data(), r_vec->data(), b_vec->data(),
            idx0_vec->data(), idx1_vec->data(), w0_vec->data(), w1_vec->data(),
            sizeA0, sizeA1, (cudaStream_t)stream /* -> */, y_vec->data()
        );
    } else {
        // StoreTrace: z=traceZ 累加，y=traceY 写 σ(z)
        Vector<float> *trace_z_vec = reinterpret_cast<Vector<float>*>(traceZ);
        Vector<float> *trace_y_vec = reinterpret_cast<Vector<float>*>(traceY);
        cnn_forward_layer_trace(
            x_vec->data(), q_vec->data(), l_vec->data(), r_vec->data(), b_vec->data(),
            idx0_vec->data(), idx1_vec->data(), w0_vec->data(), w1_vec->data(),
            sizeA0, sizeA1, (cudaStream_t)stream /* -> */, y_vec->data(), trace_z_vec->data(), trace_y_vec->data()
        );
    }

    // 不 cudaStreamSynchronize：热路径，每次同步在 Windows WDDM 下开销 5-20ms。
    // 执行期错误由 tick 末尾 behavior.copyToHost 的隐式同步捕获。
}

void cnn_refresh_cache_bridge(
    intptr_t p, int sizeA0, int sizeA1, intptr_t stream /* -> */,
    intptr_t idx0, intptr_t idx1, intptr_t w0, intptr_t w1)
{
    Vector<float> *p_vec    = reinterpret_cast<Vector<float>*>(p);
    Vector<int>   *idx0_vec = reinterpret_cast<Vector<int>*>(idx0);
    Vector<int>   *idx1_vec = reinterpret_cast<Vector<int>*>(idx1);
    Vector<float> *w0_vec   = reinterpret_cast<Vector<float>*>(w0);
    Vector<float> *w1_vec   = reinterpret_cast<Vector<float>*>(w1);

    cnn_refresh_cache(
        p_vec->data(), sizeA0, sizeA1, (cudaStream_t)stream /* -> */,
        idx0_vec->data(), idx1_vec->data(), w0_vec->data(), w1_vec->data()
    );
}
