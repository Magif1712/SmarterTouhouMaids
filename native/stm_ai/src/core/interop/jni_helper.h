#pragma once
#include <jni.h>
#include <string>
#include <cstring>

/**
 * JNI 命名助手宏
 *
 * 假定 Java 包路径前缀为: com.github.magif1712.smarter_touhou_maids
 * 对应 JNI 前缀: Java_com_github_magif1712_smarter_1touhou_1maids
 */

#define JNI_PKG_PREFIX Java_com_github_magif1712_smarter_1touhou_1maids

// 定义 JNI 方法名的宏
// pkg: core_containers_vector
// cls: VectorNative
// method: _createVectorBool
// 展开后: Java_com_github_magif1712_smarter_1touhou_1maids_core_1containers_1vector_VectorNative_1createVectorBool
// The indirection layer for stringification
#define JNI_METHOD_IMPL(prefix, pkg, cls, method) JNI_METHOD_IMPL2(prefix, pkg, cls, method)

// The actual implementation of stringification
#define JNI_METHOD_IMPL2(prefix, pkg, cls, method) prefix##_##pkg##_##cls##_##method
#define JNI_METHOD(pkg, cls, method) JNI_METHOD_IMPL(JNI_PKG_PREFIX, pkg, cls, method)

namespace jni_helper {
    // 把 C++ 异常翻译成 Java RuntimeException。
    // 若 env 上已有 pending 异常则不再覆盖，避免丢失原始上下文。
    inline void throw_runtime_exception(JNIEnv* env, const char* message) {
        if (env == nullptr) return;
        if (env->ExceptionCheck()) return; // 已有 pending 异常，保留原异常
        jclass exClass = env->FindClass("java/lang/RuntimeException");
        if (exClass != nullptr) {
            env->ThrowNew(exClass, message);
            env->DeleteLocalRef(exClass);
        }
    }

    inline void throw_runtime_exception(JNIEnv* env, const std::string& message) {
        throw_runtime_exception(env, message.c_str());
    }
}

/**
 * 统一的 JNI 异常翻译宏。用于在 JNI 函数体尾部捕获所有 C++ 异常并翻译为
 * Java RuntimeException，避免异常穿过 extern "C" JNI 边界导致 JVM 崩溃
 * (EXCEPTION_UNCAUGHT_CXX_EXCEPTION)。
 *
 * 用法:
 *   JNIEXPORT void JNICALL JNI_METHOD(...) {
 *       try {
 *           ... // 可能抛 C++ 异常的 native 代码
 *       }
 *       JNI_CATCH_TRANSLATE(env, "_someMethod")
 *   }
 *
 * 对于非 void 返回类型的函数，宏之后需要补 return 默认值，例如:
 *   JNI_CATCH_TRANSLATE(env, "_create")
 *   return 0;
 */
#define JNI_CATCH_TRANSLATE(env, ctx) \
    catch (const std::exception& _jni_e) { \
        jni_helper::throw_runtime_exception(env, (std::string(ctx) + ": " + _jni_e.what())); \
    } catch (...) { \
        jni_helper::throw_runtime_exception(env, std::string(ctx) + ": unknown native exception"); \
    }