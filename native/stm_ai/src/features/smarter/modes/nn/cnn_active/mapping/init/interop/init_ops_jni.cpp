#include <jni.h>
#include "core/interop/jni_helper.h"
#include "init_ops_bridge.h"

#ifdef __cplusplus
extern "C" {
#endif

// Java: ...urana_process.fittable_mapper.cnn_active_mapper.nn.cnn_active.CnnActiveInitNative#_fillSymmetric
JNIEXPORT void JNICALL JNI_METHOD(features_smarter_modes_nn_cnn_1active, CnnActiveInitNative, _1fillSymmetric)(
    JNIEnv *env,
    jclass clazz,
    jfloat bound, jlong seed, jlong stream /* -> */, jlong v)
{
    try
    {
        cnn_active_fill_symmetric_bridge(bound, seed, stream /* -> */, v);
    }
    JNI_CATCH_TRANSLATE(env, "_fillSymmetric")
}

#ifdef __cplusplus
}
#endif
