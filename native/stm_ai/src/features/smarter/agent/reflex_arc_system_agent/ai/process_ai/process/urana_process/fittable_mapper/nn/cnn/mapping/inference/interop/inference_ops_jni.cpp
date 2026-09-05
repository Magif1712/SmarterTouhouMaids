#include <jni.h>
#include "core/interop/jni_helper.h"
#include "inference_ops_bridge.h"

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL JNI_METHOD(features_smarter_agent_reflex_1arc_1system_1agent_ai_process_1ai_process_urana_1process_fittable_1mapper_nn_cnn_mapping_inference, CnnInferenceOpsNative, _1cnnForwardLayer)(
    JNIEnv *env,
    jclass clazz,
    jlong x, jlong p, jlong q, jlong l, jlong r, jlong b,
    jlong idx0, jlong idx1, jlong w0, jlong w1,
    jint sizeA0, jint sizeA1, jlong stream /* -> */,
    jlong y, jlong traceZ, jlong traceY)
{
    try
    {
        cnn_forward_layer_bridge(
            x, p, q, l, r, b,
            idx0, idx1, w0, w1,
            sizeA0, sizeA1, stream /* -> */,
            y, traceZ, traceY
        );
    }
    JNI_CATCH_TRANSLATE(env, "_cnnForwardLayer")
}

JNIEXPORT void JNICALL JNI_METHOD(features_smarter_agent_reflex_1arc_1system_1agent_ai_process_1ai_process_urana_1process_fittable_1mapper_nn_cnn_mapping_inference, CnnInferenceOpsNative, _1cnnRefreshCache)(
    JNIEnv *env,
    jclass clazz,
    jlong p, jint sizeA0, jint sizeA1, jlong stream /* -> */,
    jlong idx0, jlong idx1, jlong w0, jlong w1)
{
    try
    {
        cnn_refresh_cache_bridge(
            p, sizeA0, sizeA1, stream /* -> */,
            idx0, idx1, w0, w1
        );
    }
    JNI_CATCH_TRANSLATE(env, "_cnnRefreshCache")
}

#ifdef __cplusplus
}
#endif