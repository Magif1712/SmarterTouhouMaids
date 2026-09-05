#include <jni.h>
#include "core/interop/jni_helper.h"
#include "gradient_ops_bridge.h"

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL JNI_METHOD(features_smarter_agent_reflex_1arc_1system_1agent_ai_process_1ai_process_urana_1process_fittable_1mapper_nn_cnn_mapping_training, CnnTrainingOpsNative, _1cnnBackwardLayer)(
    JNIEnv *env,
    jclass clazz,
    jlong traceZ, jlong traceY, jlong target, jlong x,
    jlong hp_p, jlong hp_q, jlong hp_l, jlong hp_r, jlong hp_b,
    jlong hp_idx0, jlong hp_idx1, jlong hp_w0, jlong hp_w1,
    jint sizeA0, jint sizeA1, jint sizeC, jfloat lr, jlong stream /* -> */,
    jlong buf_p, jlong buf_q, jlong buf_l, jlong buf_r, jlong buf_b,
    jlong buf_idx0, jlong buf_idx1, jlong buf_w0, jlong buf_w1,
    jlong dz, jlong dInput, jlong bufTc)
{
    try
    {
        cnn_backward_layer_bridge(
            traceZ, traceY, target, x,
            hp_p, hp_q, hp_l, hp_r, hp_b,
            hp_idx0, hp_idx1, hp_w0, hp_w1,
            sizeA0, sizeA1, sizeC, lr, stream /* -> */,
            buf_p, buf_q, buf_l, buf_r, buf_b,
            buf_idx0, buf_idx1, buf_w0, buf_w1,
            dz, dInput, bufTc
        );
    }
    JNI_CATCH_TRANSLATE(env, "_cnnBackwardLayer")
}

#ifdef __cplusplus
}
#endif