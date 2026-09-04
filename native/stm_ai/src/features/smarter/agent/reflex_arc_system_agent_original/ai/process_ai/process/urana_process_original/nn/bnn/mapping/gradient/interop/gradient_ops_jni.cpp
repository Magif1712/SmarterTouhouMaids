#include <jni.h>
#include "core/interop/jni_helper.h"
#include "gradient_ops_bridge.h"

extern "C" {

JNIEXPORT void JNICALL JNI_METHOD(features_smarter_agent_reflex_1arc_1system_1agent_1original_ai_process_1ai_process_urana_1process_1original_nn_bnn_mapping_training, BnnGradientOpsNative, _1backwardLayer)(
    JNIEnv* env,
    jclass clazz,
    jlong da0_handle,
    jlong da1_handle,
    jlong fz_handle,
    jlong b_handle,
    jlong p_handle,
    jlong q_handle,
    jlong l_handle,
    jlong r_handle,
    jlong dz_workspace_handle,
    jint batch_size,
    jint n_curr,
    jint n_prev,
    jlong stream_handle)
{
    auto* da0 = reinterpret_cast<Vector<int>*>(da0_handle);
    auto* da1 = reinterpret_cast<const Vector<int>*>(da1_handle);
    auto* fz = reinterpret_cast<const Vector<bool>*>(fz_handle);
    auto* b = reinterpret_cast<const Vector<bool>*>(b_handle);
    auto* p = reinterpret_cast<const Vector<int>*>(p_handle);
    auto* q = reinterpret_cast<const Vector<bool>*>(q_handle);
    auto* l = reinterpret_cast<const Vector<bool>*>(l_handle);
    auto* r = reinterpret_cast<const Vector<bool>*>(r_handle);
    auto* dz_workspace = reinterpret_cast<Vector<int>*>(dz_workspace_handle);
    try
    {
        backwardLayerBridge_original(da0, da1, fz, b, p, q, l, r, dz_workspace, batch_size, n_curr, n_prev, reinterpret_cast<cudaStream_t>(stream_handle));
    }
    JNI_CATCH_TRANSLATE(env, "_backwardLayer")
}

JNIEXPORT void JNICALL JNI_METHOD(features_smarter_agent_reflex_1arc_1system_1agent_1original_ai_process_1ai_process_urana_1process_1original_nn_bnn_mapping_training, BnnGradientOpsNative, _1backwardGradientDescentLayer)(
    JNIEnv* env,
    jclass clazz,
    jlong da0_handle,
    jlong da1_handle,
    jlong a_prev_handle,
    jlong fz_handle,
    jlong b_handle,
    jlong p_handle,
    jlong q_handle,
    jlong l_handle,
    jlong r_handle,
    jlong dz_workspace_handle,
    jint n_curr,
    jint n_prev,
    jlong stream_handle)
{
    auto* da0 = reinterpret_cast<Vector<int>*>(da0_handle);
    auto* da1 = reinterpret_cast<const Vector<int>*>(da1_handle);
    auto* a_prev = reinterpret_cast<const Vector<bool>*>(a_prev_handle);
    auto* fz = reinterpret_cast<const Vector<bool>*>(fz_handle);
    auto* b = reinterpret_cast<Vector<bool>*>(b_handle);
    auto* p = reinterpret_cast<Vector<int>*>(p_handle);
    auto* q = reinterpret_cast<Vector<bool>*>(q_handle);
    auto* l = reinterpret_cast<Vector<bool>*>(l_handle);
    auto* r = reinterpret_cast<Vector<bool>*>(r_handle);
    auto* dz_workspace = reinterpret_cast<Vector<int>*>(dz_workspace_handle);
    try
    {
        backwardGradientDescentLayerBridge_original(da0, da1, a_prev, fz, b, p, q, l, r, dz_workspace, n_curr, n_prev, reinterpret_cast<cudaStream_t>(stream_handle));
    }
    JNI_CATCH_TRANSLATE(env, "_backwardGradientDescentLayer")
}

} // extern "C"