#include <jni.h>
#include <cuda_runtime.h>
#include <stdexcept>
#include <string>
#include "core/interop/jni_helper.h"

// 显存监测组件：通过 cudaMemGetInfo 查询 GPU 显存使用情况。
// 该查询为轻量级 driver 查询，不启动内核、不搬运数据、不强制同步 GPU 工作流，
// 单次开销在微秒量级，可在每 tick 调用而不影响性能。
// 若 CUDA 上下文已损坏（如越界访问后的 sticky error），本调用会返回该错误，
// 这恰好能帮助诊断“上下文是否已损坏”。

#ifdef __cplusplus
extern "C" {
#endif

// 返回 long[2] = {freeBytes, totalBytes}。
// 失败时抛 RuntimeException 并返回 {0, 0}，避免 Java 侧 NPE。
JNIEXPORT jlongArray JNICALL JNI_METHOD(core_diagnostics, MemoryDiagnostics, _1getMemInfo)(
    JNIEnv *env,
    jclass clazz)
{
    (void)clazz;

    jlong info[2] = {0, 0};
    bool ok = false;

    try
    {
        size_t free = 0, total = 0;
        cudaError_t err = cudaMemGetInfo(&free, &total);
        if (err != cudaSuccess)
        {
            throw std::runtime_error(
                std::string("cudaMemGetInfo failed: ") + cudaGetErrorString(err));
        }
        info[0] = static_cast<jlong>(free);
        info[1] = static_cast<jlong>(total);
        ok = true;
    }
    JNI_CATCH_TRANSLATE(env, "_getMemInfo")

    jlongArray result = env->NewLongArray(2);
    if (result != nullptr)
    {
        env->SetLongArrayRegion(result, 0, 2, info);
    }
    (void)ok;
    return result;
}

#ifdef __cplusplus
}
#endif
