#include <jni.h>
#include <cstdint>
#include <vector>
#include <memory>
#include <exception>
#include <filesystem>
#include <string>
#include "../../../interop/jni_helper.h"
#include "vector_bridge.h"

// 设计说明：
// 所有 JNI 函数都使用 try-catch + JNI_CATCH_TRANSLATE 宏包裹函数体。
// 这样 bridge 层重新抛出的 C++ 异常会被捕获并转换为 Java RuntimeException，
// 避免 C++ 异常穿过 JNI 边界导致 JVM 崩溃 (EXCEPTION_UNCAUGHT_CXX_EXCEPTION)。
//
// stream 参数：jlong stream_handle 经 reinterpret_cast<cudaStream_t> 透传给 bridge。
// Java 侧无 stream 重载的方法统一传 0L（NULL 流，同步语义）。

// 把 Java jstring (UTF-16) 忠实地转为 std::filesystem::path。
// 走 GetStringChars (UTF-16) → std::u16string → std::filesystem::path：
//   - 不经过 GetStringUTFChars 的 modified-UTF-8（补充平面字符会被编成 CESU-8 双 3 字节，
//     而非标准 UTF-8 4 字节，emoji 世界名会出错）；
//   - 不经过 Windows ANSI 代码页（std::string 路径在 MSVC 下被按 ACP 解释，
//     中文路径会让 std::ofstream 打开失败）。
// std::filesystem::path 是 C++17 标准的“路径名字”原语——Windows 内部存 UTF-16、
// Linux 内部存 UTF-8，并正确交给平台文件 API，从根上消除“字节当名字”的编码类别错误。
static std::filesystem::path jstringToPath(JNIEnv *env, jstring filename)
{
    const jchar *chars = env->GetStringChars(filename, nullptr);
    jsize len = env->GetStringLength(filename);
    std::filesystem::path path;
    if (chars && len > 0)
    {
        std::u16string u16(reinterpret_cast<const char16_t *>(chars), static_cast<size_t>(len));
        path = std::filesystem::path(u16);
    }
    if (chars)
        env->ReleaseStringChars(filename, chars);
    return path;
}

extern "C"
{

    JNIEXPORT jlong JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1createVectorBool)(JNIEnv *env, jclass)
    {
        try
        {
            auto *vec = VectorCreateBool();
            return reinterpret_cast<jlong>(vec);
        }
        JNI_CATCH_TRANSLATE(env, "_createVectorBool")
        return 0;
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1allocateBool)(JNIEnv *env, jclass, jlong handle, jint size)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<bool> *>(handle);
            if (vec)
                VectorAllocateBool(vec, static_cast<size_t>(size));
        }
        JNI_CATCH_TRANSLATE(env, "_allocateBool")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1deleteVectorBool)(JNIEnv *env, jclass, jlong handle)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<bool> *>(handle);
            if (vec)
                VectorDeleteBool(vec);
        }
        JNI_CATCH_TRANSLATE(env, "_deleteVectorBool")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1copyFromHostBool)(JNIEnv *env, jclass, jlong handle, jintArray data, jint count, jlong stream_handle)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<bool> *>(handle);
            if (!vec || !data)
                return;

            jint *j_data = env->GetIntArrayElements(data, nullptr);
            VectorCopyFromHostBool(vec, reinterpret_cast<const uint32_t *>(j_data), static_cast<size_t>(count), reinterpret_cast<cudaStream_t>(stream_handle));
            env->ReleaseIntArrayElements(data, j_data, JNI_ABORT);
        }
        JNI_CATCH_TRANSLATE(env, "_copyFromHostBool")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1copyToHostBool)(JNIEnv *env, jclass, jlong handle, jintArray data, jint count)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<bool> *>(handle);
            if (!vec || !data)
                return;

            jint *j_data = env->GetIntArrayElements(data, nullptr);
            VectorCopyToHostBool(vec, reinterpret_cast<uint32_t *>(j_data), static_cast<size_t>(count));
            env->ReleaseIntArrayElements(data, j_data, 0);
        }
        JNI_CATCH_TRANSLATE(env, "_copyToHostBool")
    }

    // 指定 stream 上的同步 D2H：cudaMemcpyAsync(D2H, stream) + cudaStreamSynchronize(stream)。
    // 单流同步，不 drain 其它流（GL 渲染流不受影响）。供效应器工作线程读出 behavior 到 host。
    // sync 已保证 D2H 写完，故 ReleaseIntArrayElements 用 mode=0（提交写入）是安全的。
    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1copyToHostBoolSync)(JNIEnv *env, jclass, jlong handle, jintArray data, jint count, jlong stream_handle)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<bool> *>(handle);
            if (!vec || !data)
                return;

            jint *j_data = env->GetIntArrayElements(data, nullptr);
            VectorCopyToHostBoolSync(vec, reinterpret_cast<uint32_t *>(j_data), static_cast<size_t>(count), reinterpret_cast<cudaStream_t>(stream_handle));
            env->ReleaseIntArrayElements(data, j_data, 0);
        }
        JNI_CATCH_TRANSLATE(env, "_copyToHostBoolSync")
    }

    // 分配为 host mapped pinned memory（zero-copy）。GPU 经 device 视图写 = 写 host 内存，host 直接读。
    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1allocateBoolMapped)(JNIEnv *env, jclass, jlong handle, jint size)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<bool> *>(handle);
            if (vec)
                VectorAllocateBoolMapped(vec, static_cast<size_t>(size));
        }
        JNI_CATCH_TRANSLATE(env, "_allocateBoolMapped")
    }

    // 从 mapped host 内存读取到 Java int[]（纯 host memcpy，零 CUDA 调用，不 flush WDDM 命令缓冲）。
    // 主线程 20Hz 调用：先由调用方检查 generation 判断 GPU 已写完，再调此方法读完整 behavior，无撕裂。
    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1readMappedBool)(JNIEnv *env, jclass, jlong handle, jintArray data, jint count)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<bool> *>(handle);
            if (!vec || !data)
                return;

            jint *j_data = env->GetIntArrayElements(data, nullptr);
            VectorReadMappedBool(vec, reinterpret_cast<uint32_t *>(j_data), static_cast<size_t>(count));
            env->ReleaseIntArrayElements(data, j_data, 0);
        }
        JNI_CATCH_TRANSLATE(env, "_readMappedBool")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1saveBool)(JNIEnv *env, jclass, jlong handle, jstring filename)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<bool> *>(handle);
            if (!vec || !filename)
                return;

            std::filesystem::path path = jstringToPath(env, filename);
            VectorSaveBool(vec, path);
        }
        JNI_CATCH_TRANSLATE(env, "_saveBool")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1loadFromFileBool)(JNIEnv *env, jclass, jlong handle, jstring filename)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<bool> *>(handle);
            if (!vec || !filename)
                return;

            std::filesystem::path path = jstringToPath(env, filename);
            VectorLoadFromFileBool(vec, path);
        }
        JNI_CATCH_TRANSLATE(env, "_loadFromFileBool")
    }

    JNIEXPORT jint JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1getSizeBool)(JNIEnv *env, jclass, jlong handle)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<bool> *>(handle);
            return static_cast<jint>(VectorGetSizeBool(vec));
        }
        JNI_CATCH_TRANSLATE(env, "_getSizeBool")
        return 0;
    }

    JNIEXPORT jlong JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1createVectorInt)(JNIEnv *env, jclass)
    {
        try
        {
            auto *vec = VectorCreateInt();
            return reinterpret_cast<jlong>(vec);
        }
        JNI_CATCH_TRANSLATE(env, "_createVectorInt")
        return 0;
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1allocateInt)(JNIEnv *env, jclass, jlong handle, jint size)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<int> *>(handle);
            if (vec)
                VectorAllocateInt(vec, static_cast<size_t>(size));
        }
        JNI_CATCH_TRANSLATE(env, "_allocateInt")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1deleteVectorInt)(JNIEnv *env, jclass, jlong handle)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<int> *>(handle);
            if (vec)
                VectorDeleteInt(vec);
        }
        JNI_CATCH_TRANSLATE(env, "_deleteVectorInt")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1copyFromHostInt)(JNIEnv *env, jclass, jlong handle, jintArray data, jint count, jlong stream_handle)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<int> *>(handle);
            if (!vec || !data)
                return;

            jint *j_data = env->GetIntArrayElements(data, nullptr);
            VectorCopyFromHostInt(vec, reinterpret_cast<const int *>(j_data), static_cast<size_t>(count), reinterpret_cast<cudaStream_t>(stream_handle));
            env->ReleaseIntArrayElements(data, j_data, JNI_ABORT);
        }
        JNI_CATCH_TRANSLATE(env, "_copyFromHostInt")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1copyToHostInt)(JNIEnv *env, jclass, jlong handle, jintArray data, jint count)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<int> *>(handle);
            if (!vec || !data)
                return;

            jint *j_data = env->GetIntArrayElements(data, nullptr);
            VectorCopyToHostInt(vec, reinterpret_cast<int *>(j_data), static_cast<size_t>(count));
            env->ReleaseIntArrayElements(data, j_data, 0);
        }
        JNI_CATCH_TRANSLATE(env, "_copyToHostInt")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1saveInt)(JNIEnv *env, jclass, jlong handle, jstring filename)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<int> *>(handle);
            if (!vec || !filename)
                return;

            std::filesystem::path path = jstringToPath(env, filename);
            VectorSaveInt(vec, path);
        }
        JNI_CATCH_TRANSLATE(env, "_saveInt")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1loadFromFileInt)(JNIEnv *env, jclass, jlong handle, jstring filename)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<int> *>(handle);
            if (!vec || !filename)
                return;

            std::filesystem::path path = jstringToPath(env, filename);
            VectorLoadFromFileInt(vec, path);
        }
        JNI_CATCH_TRANSLATE(env, "_loadFromFileInt")
    }

    JNIEXPORT jint JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1getSizeInt)(JNIEnv *env, jclass, jlong handle)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<int> *>(handle);
            return static_cast<jint>(VectorGetSizeInt(vec));
        }
        JNI_CATCH_TRANSLATE(env, "_getSizeInt")
        return 0;
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1copyRegionFromInt)(JNIEnv *env, jclass, jlong dst_handle, jlong dst_offset, jlong src_handle, jlong src_offset, jlong count, jlong stream_handle)
    {
        try
        {
            auto *dst = reinterpret_cast<Vector<int> *>(dst_handle);
            auto *src = reinterpret_cast<Vector<int> *>(src_handle);
            if (dst && src)
            {
                VectorCopyRegionFromInt(dst, static_cast<size_t>(dst_offset), src, static_cast<size_t>(src_offset), static_cast<size_t>(count), reinterpret_cast<cudaStream_t>(stream_handle));
            }
        }
        JNI_CATCH_TRANSLATE(env, "_copyRegionFromInt")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1setRegionInt)(JNIEnv *env, jclass, jlong dst_handle, jlong dst_offset, jlong src_handle, jlong stream_handle)
    {
        try
        {
            auto *dst = reinterpret_cast<Vector<int> *>(dst_handle);
            auto *src = reinterpret_cast<Vector<int> *>(src_handle);
            if (dst && src)
            {
                VectorSetRegionInt(dst, static_cast<size_t>(dst_offset), src, reinterpret_cast<cudaStream_t>(stream_handle));
            }
        }
        JNI_CATCH_TRANSLATE(env, "_setRegionInt")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1copyRegionFromHostInt)(JNIEnv *env, jclass, jlong dst_handle, jlong dst_offset, jintArray src_array, jlong count, jlong stream_handle)
    {
        try
        {
            auto *dst = reinterpret_cast<Vector<int> *>(dst_handle);
            if (!dst || !src_array)
                return;

            jint *src_ptr = env->GetIntArrayElements(src_array, nullptr);
            if (src_ptr == nullptr)
                return; // 内存不足

            VectorCopyRegionFromHostInt(dst, static_cast<size_t>(dst_offset), reinterpret_cast<const int *>(src_ptr), static_cast<size_t>(count), reinterpret_cast<cudaStream_t>(stream_handle));

            env->ReleaseIntArrayElements(src_array, src_ptr, JNI_ABORT);
        }
        JNI_CATCH_TRANSLATE(env, "_copyRegionFromHostInt")
    }

    // ===================================================================
    // Vector<float> 接口（与 Vector<int> 对称：CNN 浮点权重/激活/梯度）
    // ===================================================================

    JNIEXPORT jlong JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1createVectorFloat)(JNIEnv *env, jclass)
    {
        try
        {
            auto *vec = VectorCreateFloat();
            return reinterpret_cast<jlong>(vec);
        }
        JNI_CATCH_TRANSLATE(env, "_createVectorFloat")
        return 0;
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1allocateFloat)(JNIEnv *env, jclass, jlong handle, jint size)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<float> *>(handle);
            if (vec)
                VectorAllocateFloat(vec, static_cast<size_t>(size));
        }
        JNI_CATCH_TRANSLATE(env, "_allocateFloat")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1deleteVectorFloat)(JNIEnv *env, jclass, jlong handle)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<float> *>(handle);
            if (vec)
                VectorDeleteFloat(vec);
        }
        JNI_CATCH_TRANSLATE(env, "_deleteVectorFloat")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1copyFromHostFloat)(JNIEnv *env, jclass, jlong handle, jfloatArray data, jint count, jlong stream_handle)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<float> *>(handle);
            if (!vec || !data)
                return;

            jfloat *j_data = env->GetFloatArrayElements(data, nullptr);
            VectorCopyFromHostFloat(vec, reinterpret_cast<const float *>(j_data), static_cast<size_t>(count), reinterpret_cast<cudaStream_t>(stream_handle));
            env->ReleaseFloatArrayElements(data, j_data, JNI_ABORT);
        }
        JNI_CATCH_TRANSLATE(env, "_copyFromHostFloat")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1copyToHostFloat)(JNIEnv *env, jclass, jlong handle, jfloatArray data, jint count)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<float> *>(handle);
            if (!vec || !data)
                return;

            jfloat *j_data = env->GetFloatArrayElements(data, nullptr);
            VectorCopyToHostFloat(vec, reinterpret_cast<float *>(j_data), static_cast<size_t>(count));
            env->ReleaseFloatArrayElements(data, j_data, 0);
        }
        JNI_CATCH_TRANSLATE(env, "_copyToHostFloat")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1saveFloat)(JNIEnv *env, jclass, jlong handle, jstring filename)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<float> *>(handle);
            if (!vec || !filename)
                return;

            std::filesystem::path path = jstringToPath(env, filename);
            VectorSaveFloat(vec, path);
        }
        JNI_CATCH_TRANSLATE(env, "_saveFloat")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1loadFromFileFloat)(JNIEnv *env, jclass, jlong handle, jstring filename)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<float> *>(handle);
            if (!vec || !filename)
                return;

            std::filesystem::path path = jstringToPath(env, filename);
            VectorLoadFromFileFloat(vec, path);
        }
        JNI_CATCH_TRANSLATE(env, "_loadFromFileFloat")
    }

    JNIEXPORT jint JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1getSizeFloat)(JNIEnv *env, jclass, jlong handle)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<float> *>(handle);
            return static_cast<jint>(VectorGetSizeFloat(vec));
        }
        JNI_CATCH_TRANSLATE(env, "_getSizeFloat")
        return 0;
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1copyRegionFromFloat)(JNIEnv *env, jclass, jlong dst_handle, jlong dst_offset, jlong src_handle, jlong src_offset, jlong count, jlong stream_handle)
    {
        try
        {
            auto *dst = reinterpret_cast<Vector<float> *>(dst_handle);
            auto *src = reinterpret_cast<Vector<float> *>(src_handle);
            if (dst && src)
            {
                VectorCopyRegionFromFloat(dst, static_cast<size_t>(dst_offset), src, static_cast<size_t>(src_offset), static_cast<size_t>(count), reinterpret_cast<cudaStream_t>(stream_handle));
            }
        }
        JNI_CATCH_TRANSLATE(env, "_copyRegionFromFloat")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1setRegionFloat)(JNIEnv *env, jclass, jlong dst_handle, jlong dest_offset, jlong src_handle, jlong stream_handle)
    {
        try
        {
            auto *dst = reinterpret_cast<Vector<float> *>(dst_handle);
            auto *src = reinterpret_cast<Vector<float> *>(src_handle);
            if (dst && src)
            {
                VectorSetRegionFloat(dst, static_cast<size_t>(dest_offset), src, reinterpret_cast<cudaStream_t>(stream_handle));
            }
        }
        JNI_CATCH_TRANSLATE(env, "_setRegionFloat")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1copyRegionFromHostFloat)(JNIEnv *env, jclass, jlong dst_handle, jlong dst_offset, jfloatArray src_array, jlong count, jlong stream_handle)
    {
        try
        {
            auto *dst = reinterpret_cast<Vector<float> *>(dst_handle);
            if (!dst || !src_array)
                return;

            jfloat *src_ptr = env->GetFloatArrayElements(src_array, nullptr);
            if (src_ptr == nullptr)
                return; // 内存不足

            VectorCopyRegionFromHostFloat(dst, static_cast<size_t>(dst_offset), reinterpret_cast<const float *>(src_ptr), static_cast<size_t>(count), reinterpret_cast<cudaStream_t>(stream_handle));

            env->ReleaseFloatArrayElements(src_array, src_ptr, JNI_ABORT);
        }
        JNI_CATCH_TRANSLATE(env, "_copyRegionFromHostFloat")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1multiplyByScalarFloat)(JNIEnv *env, jclass, jlong handle, jfloat scalar, jlong offset, jlong length, jlong stream_handle)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<float> *>(handle);
            if (vec)
            {
                VectorMultiplyByScalarFloat(vec, static_cast<float>(scalar), static_cast<size_t>(offset), static_cast<size_t>(length), reinterpret_cast<cudaStream_t>(stream_handle));
            }
        }
        JNI_CATCH_TRANSLATE(env, "_multiplyByScalarFloat")
    }

    // 用 PCG 随机填充浮点向量，元素 ∈ [0, bound)（CNN 权重初始化）。同步：bridge 内 cudaStreamSynchronize(0)。
    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1fillRandomFloat)(JNIEnv *env, jclass, jlong handle, jfloat bound, jlong seed)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<float> *>(handle);
            if (vec)
            {
                VectorFillRandomFloat(vec, static_cast<float>(bound), static_cast<uint64_t>(seed));
            }
        }
        JNI_CATCH_TRANSLATE(env, "_fillRandomFloat")
    }

    // 分配为 host mapped pinned memory（zero-copy）。GPU 经 device 视图写 = 写 host 内存，host 直接读。
    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1allocateFloatMapped)(JNIEnv *env, jclass, jlong handle, jint size)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<float> *>(handle);
            if (vec)
                VectorAllocateFloatMapped(vec, static_cast<size_t>(size));
        }
        JNI_CATCH_TRANSLATE(env, "_allocateFloatMapped")
    }

    // 从 mapped host 内存读取到 Java float[]（纯 host memcpy，零 CUDA 调用，不 flush WDDM 命令缓冲）。
    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1readMappedFloat)(JNIEnv *env, jclass, jlong handle, jfloatArray data, jint count)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<float> *>(handle);
            if (!vec || !data)
                return;

            jfloat *j_data = env->GetFloatArrayElements(data, nullptr);
            VectorReadMappedFloat(vec, reinterpret_cast<float *>(j_data), static_cast<size_t>(count));
            env->ReleaseFloatArrayElements(data, j_data, 0);
        }
        JNI_CATCH_TRANSLATE(env, "_readMappedFloat")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1scatterBits)(JNIEnv *env, jclass, jlong srcHandle, jlong dstHandle, jlong pHandle)
    {
        try
        {
            auto *src = reinterpret_cast<Vector<bool> *>(srcHandle);
            auto *dst = reinterpret_cast<Vector<bool> *>(dstHandle);
            auto *p = reinterpret_cast<Vector<int> *>(pHandle);
            if (src && dst && p)
            {
                VectorScatterBits(src, dst, p);
            }
        }
        JNI_CATCH_TRANSLATE(env, "_scatterBits")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1xorBool)(JNIEnv *env, jclass, jlong dstHandle, jlong srcHandle)
    {
        try
        {
            auto *dst = reinterpret_cast<Vector<bool> *>(dstHandle);
            auto *src = reinterpret_cast<Vector<bool> *>(srcHandle);
            if (dst && src)
            {
                VectorXorBool(dst, src);
            }
        }
        JNI_CATCH_TRANSLATE(env, "_xorBool")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1setRegionBool)(JNIEnv *env, jclass, jlong dstHandle, jlong destOffset, jlong srcHandle, jlong stream_handle)
    {
        try
        {
            auto *dst = reinterpret_cast<Vector<bool> *>(dstHandle);
            auto *src = reinterpret_cast<Vector<bool> *>(srcHandle);
            if (dst && src)
            {
                VectorSetRegionBool(dst, static_cast<size_t>(destOffset), src, reinterpret_cast<cudaStream_t>(stream_handle));
            }
        }
        JNI_CATCH_TRANSLATE(env, "_setRegionBool")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1copyRegionFromBool)(JNIEnv *env, jclass, jlong dstHandle, jlong dstOffset, jlong srcHandle, jlong srcOffset, jlong numBits, jlong stream_handle)
    {
        try
        {
            auto *dst = reinterpret_cast<Vector<bool> *>(dstHandle);
            auto *src = reinterpret_cast<Vector<bool> *>(srcHandle);
            if (dst && src)
            {
                VectorCopyRegionFromBool(
                    dst,
                    static_cast<size_t>(dstOffset),
                    src,
                    static_cast<size_t>(srcOffset),
                    static_cast<size_t>(numBits),
                    reinterpret_cast<cudaStream_t>(stream_handle));
            }
        }
        JNI_CATCH_TRANSLATE(env, "_copyRegionFromBool")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1copyRegionFromHostBool)(
        JNIEnv *env, jclass, jlong handle, jlong dest_offset, jbooleanArray src_data, jint num_bits, jlong stream_handle)
    {
        jboolean *src_ptr = env->GetBooleanArrayElements(src_data, nullptr);
        if (src_ptr == nullptr)
        {
            return;
        }

        try
        {
            auto *dst = reinterpret_cast<Vector<bool> *>(handle);
            VectorCopyRegionFromHostBool(dst, dest_offset, src_ptr, num_bits, reinterpret_cast<cudaStream_t>(stream_handle));
        }
        JNI_CATCH_TRANSLATE(env, "_copyRegionFromHostBool")

        env->ReleaseBooleanArrayElements(src_data, src_ptr, 0);
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1subtractBool)(
        JNIEnv *env, jclass, jlong aHandle, jlong bHandle, jlong bitOffset, jlong cHandle, jlong cIntOffset, jlong bitLength, jlong stream_handle)
    {
        try
        {
            auto *a = reinterpret_cast<Vector<bool> *>(aHandle);
            auto *b = reinterpret_cast<Vector<bool> *>(bHandle);
            auto *c = reinterpret_cast<Vector<int> *>(cHandle);

            if (a && b && c)
            {
                VectorSubtractBool(a, b, bitOffset, c, cIntOffset, bitLength, reinterpret_cast<cudaStream_t>(stream_handle));
            }
        }
        JNI_CATCH_TRANSLATE(env, "_subtractBool")
    }

    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1multiplyByScalarInt)(JNIEnv *env, jclass, jlong handle, jint scalar, jlong offset, jlong length, jlong stream_handle)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<int> *>(handle);
            if (vec)
            {
                VectorMultiplyByScalarInt(vec, scalar, static_cast<size_t>(offset), static_cast<size_t>(length), reinterpret_cast<cudaStream_t>(stream_handle));
            }
        }
        JNI_CATCH_TRANSLATE(env, "_multiplyByScalarInt")
    }

    // 用 PCG 随机填充位向量（BNN 权重初始化）。同步：bridge 内 cudaStreamSynchronize(0)。
    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1fillRandomBool)(JNIEnv *env, jclass, jlong handle, jlong seed)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<bool> *>(handle);
            if (vec)
            {
                VectorFillRandomBool(vec, static_cast<uint64_t>(seed));
            }
        }
        JNI_CATCH_TRANSLATE(env, "_fillRandomBool")
    }

    // 用 PCG 随机填充整数向量，元素 ∈ [0, maxVal)（BNN 目标索引 P 初始化）。同步：bridge 内 cudaStreamSynchronize(0)。
    JNIEXPORT void JNICALL JNI_METHOD(core_containers_vector, VectorNative, _1fillRandomInt)(JNIEnv *env, jclass, jlong handle, jint maxVal, jlong seed)
    {
        try
        {
            auto *vec = reinterpret_cast<Vector<int> *>(handle);
            if (vec)
            {
                VectorFillRandomInt(vec, maxVal, static_cast<uint64_t>(seed));
            }
        }
        JNI_CATCH_TRANSLATE(env, "_fillRandomInt")
    }


}