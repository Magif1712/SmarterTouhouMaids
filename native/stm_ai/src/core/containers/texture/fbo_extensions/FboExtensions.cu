#include "FboExtensions.h"
#include <stdexcept>

// 函数指针定义（对应 FboExtensions.h 的 extern 声明）。
GlGenFramebuffersProcPtr s_glGenFramebuffers = nullptr;
GlDeleteFramebuffersProcPtr s_glDeleteFramebuffers = nullptr;
GlBindFramebufferProcPtr s_glBindFramebuffer = nullptr;
GlFramebufferTexture2DProcPtr s_glFramebufferTexture2D = nullptr;
GlBlitFramebufferProcPtr s_glBlitFramebuffer = nullptr;
GlCheckFramebufferStatusProcPtr s_glCheckFramebufferStatus = nullptr;
// DSA 指针可能为 NULL（context < 4.5），NULL 时调用方走 bind 回退。
GlNamedFramebufferTextureProcPtr s_glNamedFramebufferTexture = nullptr;
GlBlitNamedFramebufferProcPtr s_glBlitNamedFramebuffer = nullptr;

void* getGlProc(const char* name) {
#ifdef _WIN32
    return reinterpret_cast<void*>(wglGetProcAddress(name));
#else
    return reinterpret_cast<void*>(glXGetProcAddress(reinterpret_cast<const GLubyte*>(name)));
#endif
}

void initFboExtensions() {
    if (s_glGenFramebuffers) return;  // 只加载一次

    // 必选：GL 3.0 FBO/blit（snapshotFrom 回退路径依赖，必加载成功）
    s_glGenFramebuffers        = reinterpret_cast<GlGenFramebuffersProcPtr>(getGlProc("glGenFramebuffers"));
    s_glDeleteFramebuffers     = reinterpret_cast<GlDeleteFramebuffersProcPtr>(getGlProc("glDeleteFramebuffers"));
    s_glBindFramebuffer        = reinterpret_cast<GlBindFramebufferProcPtr>(getGlProc("glBindFramebuffer"));
    s_glFramebufferTexture2D  = reinterpret_cast<GlFramebufferTexture2DProcPtr>(getGlProc("glFramebufferTexture2D"));
    s_glBlitFramebuffer        = reinterpret_cast<GlBlitFramebufferProcPtr>(getGlProc("glBlitFramebuffer"));
    s_glCheckFramebufferStatus = reinterpret_cast<GlCheckFramebufferStatusProcPtr>(getGlProc("glCheckFramebufferStatus"));
    if (!s_glGenFramebuffers || !s_glDeleteFramebuffers || !s_glBindFramebuffer ||
        !s_glFramebufferTexture2D || !s_glBlitFramebuffer || !s_glCheckFramebufferStatus) {
        throw std::runtime_error("Failed to load framebuffer/blit GL extensions (GL 3.0+ required)");
    }

    // 可选：GL 4.5 DSA（本 mod 用户 100% 可用；NULL 时 snapshotFrom 走 bind 回退，仍零报错）
    s_glNamedFramebufferTexture = reinterpret_cast<GlNamedFramebufferTextureProcPtr>(getGlProc("glNamedFramebufferTexture"));
    s_glBlitNamedFramebuffer    = reinterpret_cast<GlBlitNamedFramebufferProcPtr>(getGlProc("glBlitNamedFramebuffer"));
}
