#include "vision_bridge.h"
#include "core/interop/jni_helper.h"
#include <cstdint>
#include <exception>

extern "C" {

/**
 * JNI 入口：captureScreenViaSnapshot（CPU侧异步，GPU侧顺序执行）。
 *
 * 注意：此方法 CPU 侧立即返回，但 CUDA kernel 可能尚未完成。
 * 调用方需自行管理同步时机。
 */
JNIEXPORT void JNICALL
JNI_METHOD(features_smarter_agent_reflex_1arc_1system_1agent_sensor_possession_1sensor_vision, VisionNative, _1captureScreenViaSnapshot)(
    JNIEnv* env, jclass clazz,
    jint textureId,
    jlong tempTextureHandle,
    jint tempTextureOpenGLId,
    jlong tempCudaResourceHandle,
    jlong handle,
    jlong bitOffset,
    jint texWidth,
    jint texHeight,
    jint aiWidth,
    jint aiHeight,
    jlong stream_handle
) {
    try {
        auto* temp_texture = reinterpret_cast<Texture*>(tempTextureHandle);
        auto* bool_vector = reinterpret_cast<Vector<bool>*>(handle);
        cudaStream_t stream = reinterpret_cast<cudaStream_t>(stream_handle);

        cudaError_t err = captureScreenViaSnapshotBridge(
            static_cast<GLuint>(textureId),
            temp_texture,
            bool_vector,
            static_cast<size_t>(bitOffset),
            static_cast<int>(texWidth),
            static_cast<int>(texHeight),
            static_cast<int>(aiWidth),
            static_cast<int>(aiHeight),
            stream
        );

        if (err != cudaSuccess) {
            jni_helper::throw_runtime_exception(env,
                std::string("captureScreenViaSnapshot failed: ") + cudaGetErrorString(err));
        }

    }
    JNI_CATCH_TRANSLATE(env, "_captureScreenViaSnapshot")
}

JNIEXPORT void JNICALL
JNI_METHOD(features_smarter_agent_reflex_1arc_1system_1agent_sensor_possession_1sensor_vision, VisionNative, _1cleanup)(
    JNIEnv* env, jclass clazz
) {
    try {
        cleanupVisionBridge();
    }
    JNI_CATCH_TRANSLATE(env, "_cleanup")
}

} // extern "C"