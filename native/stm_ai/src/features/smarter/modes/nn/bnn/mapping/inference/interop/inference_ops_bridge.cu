#include "inference_ops_bridge.h"
#include "../inference_ops.h"
#include "core/containers/vector/Vector.h"
#include <stdexcept>
#include <string>

// 重要：Java 传入的句柄是 Vector<bool>/Vector<int> 的「对象指针」（new Vector<T>() 的地址），
// 不是 CUDA 设备数据指针。必须调用 ->data() 取出 d_data（设备指针）再传给内核。
// 绝不能把对象指针直接 cast 成 uint32_t* —— 那会把 host 堆地址当 device 用，内核访问立刻 illegal memory access。
// （对照 gradient_ops_bridge.cu 的正确范式：da0->data()）
void bnn_forward_layer_bridge_storefz_original(
    intptr_t a_prev_pad, intptr_t q, intptr_t P,
    intptr_t l, intptr_t r, intptr_t b,
    intptr_t a_curr, intptr_t fz, intptr_t n, intptr_t n_words,
    intptr_t stream)
{
    Vector<bool> *a_prev_vec = reinterpret_cast<Vector<bool>*>(a_prev_pad);
    Vector<bool> *q_vec      = reinterpret_cast<Vector<bool>*>(q);
    Vector<int>  *P_vec      = reinterpret_cast<Vector<int>*>(P);
    Vector<bool> *l_vec      = reinterpret_cast<Vector<bool>*>(l);
    Vector<bool> *r_vec      = reinterpret_cast<Vector<bool>*>(r);
    Vector<bool> *b_vec      = reinterpret_cast<Vector<bool>*>(b);
    Vector<bool> *a_curr_vec = reinterpret_cast<Vector<bool>*>(a_curr);
    Vector<bool> *fz_vec     = reinterpret_cast<Vector<bool>*>(fz);

    bnn_forward_layer_storefz_original(
        a_prev_vec->data(), q_vec->data(), P_vec->data(),
        l_vec->data(), r_vec->data(), b_vec->data(),
        a_curr_vec->data(), fz_vec->data(), (size_t)n, (size_t)n_words,
        (cudaStream_t)stream
    );

    // 不再 cudaStreamSynchronize：内核已稳定，每次同步在 Windows WDDM 下开销 5-20ms，
    // 每 tick 20 次内核 × 20ms = 400ms/tick → 3 秒一帧。
    // 执行期错误由 tick 末尾 behavior.copyToHost 的隐式同步捕获。
}

void bnn_forward_layer_bridge_nofz_original(
    intptr_t a_prev_pad, intptr_t q, intptr_t P,
    intptr_t l, intptr_t r, intptr_t b,
    intptr_t a_curr, intptr_t n, intptr_t n_words,
    intptr_t stream)
{
    Vector<bool> *a_prev_vec = reinterpret_cast<Vector<bool>*>(a_prev_pad);
    Vector<bool> *q_vec      = reinterpret_cast<Vector<bool>*>(q);
    Vector<int>  *P_vec      = reinterpret_cast<Vector<int>*>(P);
    Vector<bool> *l_vec      = reinterpret_cast<Vector<bool>*>(l);
    Vector<bool> *r_vec      = reinterpret_cast<Vector<bool>*>(r);
    Vector<bool> *b_vec      = reinterpret_cast<Vector<bool>*>(b);
    Vector<bool> *a_curr_vec = reinterpret_cast<Vector<bool>*>(a_curr);

    bnn_forward_layer_nofz_original(
        a_prev_vec->data(), q_vec->data(), P_vec->data(),
        l_vec->data(), r_vec->data(), b_vec->data(),
        a_curr_vec->data(), (size_t)n, (size_t)n_words,
        (cudaStream_t)stream
    );
}
