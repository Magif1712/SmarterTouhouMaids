#include <jni.h>
#include <exception>
#include "core/interop/jni_helper.h"
#include "bnn_ops_bridge.h"

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL JNI_METHOD(features_smarter_agent_reflex_1arc_1system_1agent_ai_process_1ai_process_urana_1process_nn_bnn_mapping, BnnOpsNative, _1negateAndBinarize)(
    JNIEnv* env, jclass clazz, jlong dst_handle, jlong src_handle, jlong stream_handle)
{
    auto* dst = reinterpret_cast<Vector<bool>*>(dst_handle);
    auto* src = reinterpret_cast<const Vector<int>*>(src_handle);

    try {
        negateAndBinarizeBridge(dst, src, reinterpret_cast<cudaStream_t>(stream_handle));
    }
    JNI_CATCH_TRANSLATE(env, "_negateAndBinarize")
}

JNIEXPORT void JNICALL JNI_METHOD(features_smarter_agent_reflex_1arc_1system_1agent_ai_process_1ai_process_urana_1process_nn_bnn_mapping, BnnOpsNative, _1negateAndBinarizeRegion)(
    JNIEnv* env,
    jclass clazz,
    jlong dst_handle,
    jlong dst_offset,
    jlong src_handle,
    jlong src_offset,
    jlong n,
    jlong stream_handle)
{
    auto* dst = reinterpret_cast<Vector<bool>*>(dst_handle);
    auto* src = reinterpret_cast<const Vector<int>*>(src_handle);

    try {
        negateAndBinarizeRegionBridge(
            dst,
            static_cast<size_t>(dst_offset),
            src,
            static_cast<size_t>(src_offset),
            static_cast<size_t>(n),
            reinterpret_cast<cudaStream_t>(stream_handle)
        );
    }
    JNI_CATCH_TRANSLATE(env, "_negateAndBinarizeRegion")
}

#ifdef __cplusplus
}
#endif
