#include <jni.h>
#include "core/interop/jni_helper.h"
#include "event_ops_bridge.h"

extern "C" {

JNIEXPORT jlong JNICALL JNI_METHOD(core_execution_event, EventNative, _1createEvent)(
    JNIEnv* env,
    jclass clazz)
{
    try
    {
        return reinterpret_cast<jlong>(createEventBridge());
    }
    JNI_CATCH_TRANSLATE(env, "_createEvent")
    return 0;
}

JNIEXPORT void JNICALL JNI_METHOD(core_execution_event, EventNative, _1recordEvent)(
    JNIEnv* env,
    jclass clazz,
    jlong event_handle,
    jlong stream_handle)
{
    try
    {
        recordEventBridge(reinterpret_cast<cudaEvent_t>(event_handle),
                          reinterpret_cast<cudaStream_t>(stream_handle));
    }
    JNI_CATCH_TRANSLATE(env, "_recordEvent")
}

JNIEXPORT void JNICALL JNI_METHOD(core_execution_event, EventNative, _1destroyEvent)(
    JNIEnv* env,
    jclass clazz,
    jlong event_handle)
{
    try
    {
        destroyEventBridge(reinterpret_cast<cudaEvent_t>(event_handle));
    }
    JNI_CATCH_TRANSLATE(env, "_destroyEvent")
}

} // extern "C"
