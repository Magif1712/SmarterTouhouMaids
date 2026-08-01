#include <jni.h>
#include <cstdint>
#include "../../../interop/jni_helper.h"
#include "counter_ops_bridge.h"

// JNI 桥接层：MappedCounter 的 native 方法。
// 沿用项目统一模式：try-catch + JNI_CATCH_TRANSLATE，jlong handle 经 reinterpret_cast 透传。
// stream 参数：jlong stream_handle 经 reinterpret_cast<cudaStream_t> 透传给 bridge。

extern "C"
{

JNIEXPORT jlong JNICALL JNI_METHOD(core_execution_counter, CounterNative, _1create)(JNIEnv *env, jclass)
{
    try
    {
        return reinterpret_cast<jlong>(CounterCreate());
    }
    JNI_CATCH_TRANSLATE(env, "_create")
    return 0;
}

JNIEXPORT void JNICALL JNI_METHOD(core_execution_counter, CounterNative, _1destroy)(JNIEnv *env, jclass, jlong handle)
{
    try
    {
        auto *counter = reinterpret_cast<MappedCounter *>(handle);
        if (counter)
            CounterDestroy(counter);
    }
    JNI_CATCH_TRANSLATE(env, "_destroy")
}

JNIEXPORT void JNICALL JNI_METHOD(core_execution_counter, CounterNative, _1increment)(JNIEnv *env, jclass, jlong handle, jlong stream_handle)
{
    try
    {
        auto *counter = reinterpret_cast<MappedCounter *>(handle);
        if (counter)
            CounterIncrement(counter, reinterpret_cast<cudaStream_t>(stream_handle));
    }
    JNI_CATCH_TRANSLATE(env, "_increment")
}

JNIEXPORT jint JNICALL JNI_METHOD(core_execution_counter, CounterNative, _1getHostValue)(JNIEnv *env, jclass, jlong handle)
{
    try
    {
        auto *counter = reinterpret_cast<MappedCounter *>(handle);
        return static_cast<jint>(CounterGetHostValue(counter));
    }
    JNI_CATCH_TRANSLATE(env, "_getHostValue")
    return 0;
}

} // extern "C"
