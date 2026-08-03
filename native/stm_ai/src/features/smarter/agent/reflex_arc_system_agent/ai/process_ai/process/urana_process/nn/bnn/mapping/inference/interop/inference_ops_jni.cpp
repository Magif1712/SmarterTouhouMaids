#include <jni.h>
#include "core/interop/jni_helper.h"
#include "inference_ops_bridge.h"

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL JNI_METHOD(features_smarter_agent_reflex_1arc_1system_1agent_ai_process_1ai_process_urana_1process_nn_bnn_mapping_inference, BnnInferenceOpsNative, _1bnnForwardLayerStoreFz)(
    JNIEnv *env,
    jclass clazz,
    jlong a_prev_pad, jlong q, jlong P,
    jlong l, jlong r, jlong b,
    jlong a_curr, jlong fz, jlong n, jlong n_words,
    jlong stream)
{
    try
    {
        bnn_forward_layer_bridge_storefz(
            a_prev_pad, q, P,
            l, r, b,
            a_curr, fz, n, n_words,
            stream
        );
    }
    JNI_CATCH_TRANSLATE(env, "_bnnForwardLayerStoreFz")
}

JNIEXPORT void JNICALL JNI_METHOD(features_smarter_agent_reflex_1arc_1system_1agent_ai_process_1ai_process_urana_1process_nn_bnn_mapping_inference, BnnInferenceOpsNative, _1bnnForwardLayerNoFz)(
    JNIEnv *env,
    jclass clazz,
    jlong a_prev_pad, jlong q, jlong P,
    jlong l, jlong r, jlong b,
    jlong a_curr, jlong n, jlong n_words,
    jlong stream)
{
    try
    {
        bnn_forward_layer_bridge_nofz(
            a_prev_pad, q, P,
            l, r, b,
            a_curr, n, n_words,
            stream
        );
    }
    JNI_CATCH_TRANSLATE(env, "_bnnForwardLayerNoFz")
}

#ifdef __cplusplus
}
#endif