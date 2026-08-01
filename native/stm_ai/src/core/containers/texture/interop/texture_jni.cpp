#include <jni.h>
#include <cstdint>
#include <vector>
#include <memory>
#include <exception>
#include "../../../../core/interop/jni_helper.h"
#include "texture_bridge.h"

// 设计说明：
// 所有 JNI 函数都使用 try-catch + JNI_CATCH_TRANSLATE 宏包裹函数体。
// 这样 bridge 层重新抛出的 C++ 异常会被捕获并转换为 Java RuntimeException，
// 避免 C++ 异常穿过 JNI 边界导致 JVM 崩溃 (EXCEPTION_UNCAUGHT_CXX_EXCEPTION)。
//
// 实现模式（对标 vector_jni.cpp）：
// - Create: 调用 TextureCreate，返回指针作为 jlong handle
// - Destroy: 调用 TextureDelete，释放资源
// - Getters: 调用 TextureGetXxx，返回属性值
// - SnapshotFrom: 调用 TextureSnapshotFrom，执行快照操作

extern "C"
{

    JNIEXPORT jlong JNICALL JNI_METHOD(core_containers_texture, TextureNative, _1createAndRegister)(
        JNIEnv *env, jclass, jint width, jint height)
    {
        try
        {
            auto* texture = TextureCreate(
                static_cast<int>(width),
                static_cast<int>(height)
            );

            if (!texture)
            {
                jni_helper::throw_runtime_exception(
                    env,
                    "_createAndRegister: TextureCreate returned nullptr"
                );
                return 0;
            }

            return reinterpret_cast<jlong>(texture);
        }
        JNI_CATCH_TRANSLATE(env, "_createAndRegister")
        return 0;
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_texture, TextureNative, _1destroy)(
        JNIEnv *env, jclass, jlong handle)
    {
        try
        {
            auto* texture = reinterpret_cast<Texture*>(handle);
            if (texture)
            {
                TextureDestroy(texture);
            }
        }
        JNI_CATCH_TRANSLATE(env, "_destroy")
    }

    JNIEXPORT jint JNICALL JNI_METHOD(core_containers_texture, TextureNative, _1getTextureId)(
        JNIEnv *env, jclass, jlong handle)
    {
        try
        {
            auto* texture = reinterpret_cast<Texture*>(handle);
            if (!texture) return 0;
            
            return static_cast<jint>(TextureGetId(texture));
        }
        JNI_CATCH_TRANSLATE(env, "_getTextureId")
        return 0;
    }

    JNIEXPORT jlong JNICALL JNI_METHOD(core_containers_texture, TextureNative, _1getCudaResourceHandle)(
        JNIEnv *env, jclass, jlong handle)
    {
        try
        {
            auto* texture = reinterpret_cast<Texture*>(handle);
            if (!texture) return 0;

            intptr_t resourceHandle = TextureGetCudaResourceHandle(texture);
            return static_cast<jlong>(resourceHandle);
        }
        JNI_CATCH_TRANSLATE(env, "_getCudaResourceHandle")
        return 0;
    }

    JNIEXPORT jint JNICALL JNI_METHOD(core_containers_texture, TextureNative, _1getWidth)(
        JNIEnv *env, jclass, jlong handle)
    {
        try
        {
            auto* texture = reinterpret_cast<Texture*>(handle);
            if (!texture) return 0;
            
            return static_cast<jint>(TextureGetWidth(texture));
        }
        JNI_CATCH_TRANSLATE(env, "_getWidth")
        return 0;
    }

    JNIEXPORT jint JNICALL JNI_METHOD(core_containers_texture, TextureNative, _1getHeight)(
        JNIEnv *env, jclass, jlong handle)
    {
        try
        {
            auto* texture = reinterpret_cast<Texture*>(handle);
            if (!texture) return 0;
            
            return static_cast<jint>(TextureGetHeight(texture));
        }
        JNI_CATCH_TRANSLATE(env, "_getHeight")
        return 0;
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_texture, TextureNative, _1snapshotFrom)(
        JNIEnv *env, jclass,
        jlong handle,
        jint srcTextureId,
        jint srcWidth,
        jint srcHeight)
    {
        try
        {
            auto* texture = reinterpret_cast<Texture*>(handle);
            if (!texture)
            {
                jni_helper::throw_runtime_exception(env, "_snapshotFrom: null texture handle");
                return;
            }

            TextureSnapshotFrom(
                texture,
                static_cast<unsigned int>(srcTextureId),
                static_cast<int>(srcWidth),
                static_cast<int>(srcHeight)
            );
        }
        JNI_CATCH_TRANSLATE(env, "_snapshotFrom")
    }

}