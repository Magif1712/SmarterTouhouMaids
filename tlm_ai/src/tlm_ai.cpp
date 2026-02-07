#include <jni.h>

#include <cstddef>
#include <cstdint>
#include <vector>

namespace {
#ifndef TLM_AI_USE_CUDA
#error "TLM_AI_USE_CUDA is required. This project does not support a CPU fallback."
#endif

struct TlmCudaEngine;
extern "C" TlmCudaEngine* tlm_cuda_create(int width, int height);
extern "C" void tlm_cuda_destroy(TlmCudaEngine* e);
extern "C" bool tlm_cuda_upload_rgb(TlmCudaEngine* e, const std::uint8_t* rgb, std::size_t len);
extern "C" int tlm_cuda_step(TlmCudaEngine* e);

struct Engine {
  int width;
  int height;
  std::vector<std::uint8_t> rgb;
  TlmCudaEngine* cuda;

  Engine(int w, int h) : width(w), height(h), rgb(static_cast<std::size_t>(w) * static_cast<std::size_t>(h) * 3u) {
    cuda = tlm_cuda_create(w, h);
  }

  ~Engine() {
    if (cuda != nullptr) {
      tlm_cuda_destroy(cuda);
      cuda = nullptr;
    }
  }
};

static void throw_runtime(JNIEnv* env, const char* msg) {
  jclass ex = env->FindClass("java/lang/RuntimeException");
  if (ex != nullptr) {
    env->ThrowNew(ex, msg);
  }
}
}

extern "C" {
JNIEXPORT jlong JNICALL Java_com_github_tartaricacid_example_client_NativeAiBackend_createEngine(JNIEnv* env, jclass, jint width,
                                                                                                jint height) {
  if (width <= 0 || height <= 0) {
    throw_runtime(env, "width/height must be > 0");
    return 0;
  }
  auto* e = new Engine(width, height);
  if (e->cuda == nullptr) {
    delete e;
    throw_runtime(env, "cuda engine init failed (no CUDA device / driver / runtime)");
    return 0;
  }
  return reinterpret_cast<jlong>(e);
}

JNIEXPORT void JNICALL Java_com_github_tartaricacid_example_client_NativeAiBackend_destroyEngine(JNIEnv*, jclass, jlong handle) {
  auto* e = reinterpret_cast<Engine*>(handle);
  delete e;
}

JNIEXPORT void JNICALL Java_com_github_tartaricacid_example_client_NativeAiBackend_setRgbFrame(JNIEnv* env, jclass, jlong handle,
                                                                                               jbyteArray rgb) {
  auto* e = reinterpret_cast<Engine*>(handle);
  if (e == nullptr || rgb == nullptr) {
    throw_runtime(env, "native engine handle is null");
    return;
  }
  const jsize len = env->GetArrayLength(rgb);
  if (len <= 0) {
    throw_runtime(env, "rgb frame is empty");
    return;
  }
  const std::size_t copy_len = static_cast<std::size_t>(len) < e->rgb.size() ? static_cast<std::size_t>(len) : e->rgb.size();
  env->GetByteArrayRegion(rgb, 0, static_cast<jsize>(copy_len), reinterpret_cast<jbyte*>(e->rgb.data()));
  if (e->cuda != nullptr) {
    if (!tlm_cuda_upload_rgb(e->cuda, e->rgb.data(), copy_len)) {
      throw_runtime(env, "cuda upload failed");
    }
    return;
  }
  throw_runtime(env, "cuda engine is not initialized");
}

JNIEXPORT jint JNICALL Java_com_github_tartaricacid_example_client_NativeAiBackend_step(JNIEnv* env, jclass, jlong handle) {
  auto* e = reinterpret_cast<Engine*>(handle);
  if (e == nullptr) {
    throw_runtime(env, "native engine handle is null");
    return 0;
  }
  if (e->cuda != nullptr) {
    int v = tlm_cuda_step(e->cuda);
    if (v < 0) {
      throw_runtime(env, "cuda step failed");
      return 0;
    }
    return static_cast<jint>(v);
  }
  throw_runtime(env, "cuda engine is not initialized");
  return 0;
}
}
