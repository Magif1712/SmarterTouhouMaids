#include <jni.h>
#include "core/interop/jni_helper.h"
#include "elastic_ops_bridge.h"

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL JNI_METHOD(features_smarter_agent_reflex_1arc_1system_1agent_ai_process_1ai_process_urana_1process_nn_bnn_standard_1bnn, ElasticOpsNative, _1reconnectOnInputChange)(
    JNIEnv* env, jclass, jlong inputHandle, jlong prevInputHandle, jlong qHandle, jlong streamHandle)
{
    try {
        auto* currentInput = reinterpret_cast<Vector<bool>*>(inputHandle);
        auto* prevInput = reinterpret_cast<Vector<bool>*>(prevInputHandle);
        auto* q = reinterpret_cast<Vector<bool>*>(qHandle);
        ElasticReconnectOnInputChange(currentInput, prevInput, q, reinterpret_cast<cudaStream_t>(streamHandle));
    }
    JNI_CATCH_TRANSLATE(env, "_reconnectOnInputChange")
}

#ifdef __cplusplus
}
#endif
