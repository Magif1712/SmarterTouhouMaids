#include <jni.h>
#include "core/interop/jni_helper.h"
#include "stream_ops_bridge.h"

extern "C" {

JNIEXPORT jlong JNICALL JNI_METHOD(core_execution_stream, StreamNative, _1createStream)(
    JNIEnv* env,
    jclass clazz)
{
    try
    {
        return reinterpret_cast<jlong>(createStreamBridge());
    }
    JNI_CATCH_TRANSLATE(env, "_createStream")
    return 0;
}

JNIEXPORT void JNICALL JNI_METHOD(core_execution_stream, StreamNative, _1destroyStream)(
    JNIEnv* env,
    jclass clazz,
    jlong stream_handle)
{
    try
    {
        destroyStreamBridge(reinterpret_cast<cudaStream_t>(stream_handle));
    }
    JNI_CATCH_TRANSLATE(env, "_destroyStream")
}

JNIEXPORT void JNICALL JNI_METHOD(core_execution_stream, StreamNative, _1synchronize)(
    JNIEnv* env,
    jclass clazz,
    jlong stream_handle)
{
    try
    {
        synchronizeStreamBridge(reinterpret_cast<cudaStream_t>(stream_handle));
    }
    JNI_CATCH_TRANSLATE(env, "_synchronize")
}

JNIEXPORT void JNICALL JNI_METHOD(core_execution_stream, StreamNative, _1waitEvent)(
    JNIEnv* env,
    jclass clazz,
    jlong stream_handle,
    jlong event_handle)
{
    try
    {
        streamWaitEventBridge(reinterpret_cast<cudaStream_t>(stream_handle),
                              reinterpret_cast<cudaEvent_t>(event_handle));
    }
    JNI_CATCH_TRANSLATE(env, "_waitEvent")
}

} // extern "C"