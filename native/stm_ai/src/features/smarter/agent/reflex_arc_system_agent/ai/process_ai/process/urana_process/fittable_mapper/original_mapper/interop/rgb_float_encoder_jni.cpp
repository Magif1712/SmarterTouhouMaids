#include "rgb_float_encoder_bridge.h"
#include "core/interop/jni_helper.h"
#include <cstdint>
#include <exception>

extern "C" {

/**
 * JNI 入口：RGB float 解码（CPU侧异步，GPU侧顺序执行）。
 *
 * 注意：此方法 CPU 侧立即返回，但 CUDA kernel 可能尚未完成。
 * 调用方需自行管理同步时机（record event，消费方 waitEvent）。
 */
JNIEXPORT void JNICALL
JNI_METHOD(features_smarter_agent_reflex_1arc_1system_1agent_ai_process_1ai_process_urana_1process_fittable_1mapper_original_1mapper, RgbFloatEncoderNative, _1encode)(
    JNIEnv* env, jclass clazz,
    jlong snapshotTextureHandle,
    jlong elementOffset,
    jlong streamHandle,
    /* -> */ jlong dstVectorHandle
) {
    try {
        auto* snapshot_texture = reinterpret_cast<Texture*>(snapshotTextureHandle);
        auto* float_vector = reinterpret_cast<Vector<float>*>(dstVectorHandle);
        cudaStream_t stream = reinterpret_cast<cudaStream_t>(streamHandle);

        cudaError_t err = rgbFloatEncodeBridge(
            snapshot_texture,
            static_cast<size_t>(elementOffset),
            stream,
            /* -> */ float_vector
        );

        if (err != cudaSuccess) {
            jni_helper::throw_runtime_exception(env,
                std::string("rgbFloatEncode failed: ") + cudaGetErrorString(err));
        }

    }
    JNI_CATCH_TRANSLATE(env, "_encode")
}

} // extern "C"
