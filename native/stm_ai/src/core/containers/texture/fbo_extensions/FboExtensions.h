#pragma once

// FBO 相关 GL 扩展函数指针的加载与持有（GL 3.0 FBO/blit + GL 4.5 DSA）。
// <p>
// 设计原则（真善美第2条）：这堆"GL 扩展加载"是 Texture 实现 FBO 缩放拷贝时的
// 底层依赖，不属于 Texture 的意识模式（管理纹理对象），故从 Texture.cu 抽出
// 独立成单元，贴近使用点放在 texture/ 下——当前只服务 Texture，不为想象中的
// 通用性过度抽象到 execution/gl 之类通用层（C 中没有的 D 中也不应有）。
// <p>
// 项目仅链接 GL 1.1 (gl.h)，FBO/blit/DSA 函数需运行时通过 wgl/glX 加载。

#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#endif

#include <GL/gl.h>

// FBO / blit 相关常量（GL 3.0+ core / ARB_framebuffer_object）
// 项目仅链接 gl.h 1.1，这些常量需手动定义。
#ifndef GL_READ_FRAMEBUFFER
#define GL_READ_FRAMEBUFFER 0x8CA8
#endif
#ifndef GL_DRAW_FRAMEBUFFER
#define GL_DRAW_FRAMEBUFFER 0x8CA9
#endif
#ifndef GL_FRAMEBUFFER
#define GL_FRAMEBUFFER 0x8D40
#endif
#ifndef GL_COLOR_ATTACHMENT0
#define GL_COLOR_ATTACHMENT0 0x8CE0
#endif
#ifndef GL_FRAMEBUFFER_COMPLETE
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5
#endif

// ==============================================
// GL framebuffer / blit 函数指针类型（GL 3.0+ core / ARB_framebuffer_object）
//
// 用 glBlitFramebuffer 替代旧的 glCopyImageSubData：
//   - glCopyImageSubData 只能 1:1 拷贝，源 > 本纹理时裁剪 → >1080p 窗口只看到左下角。
//   - glBlitFramebuffer 整源→整目标缩放，GL_NEAREST 跨步抽样，任意窗口分辨率
//     都能完整缩放到固定视网膜分辨率。
//   - 两者同走 OpenGL 命令队列：按序执行、防源被中途改写、不阻塞 CPU。
//   - GL_NEAREST 不混合 RGB，保护 bit-plane 提取完整性。
//
// DSA 优先（GL 4.5 ARB_direct_state_access）：
//   - 旧的 bind+查询+恢复 方式里 glGetIntegerv 查 GL 3.0 binding 枚举会经
//     opengl32.dll 1.1 dispatcher，返回 GL_INVALID_ENUM，每帧刷错误消息拖慢帧率。
//   - DSA 用 glNamedFramebufferTexture / glBlitNamedFramebuffer 直接按名字操作 FBO，
//     不 bind、不查询、不恢复 → 零状态污染、零 GL_INVALID_ENUM。
//   - 本 mod 用户（NVIDIA GPU + CUDA，能跑 MC 1.20.1 = Kepler+ = GL 4.6）100% 可用；
//     若 context < 4.5 指针为 NULL，snapshotFrom 回退到 bind 路径（不查询，blit 后 unbind 0）。
// ==============================================
using GlGenFramebuffersProcPtr = void (*)(GLsizei n, GLuint* framebuffers);
using GlDeleteFramebuffersProcPtr = void (*)(GLsizei n, const GLuint* framebuffers);
using GlBindFramebufferProcPtr = void (*)(GLenum target, GLuint framebuffer);
using GlFramebufferTexture2DProcPtr = void (*)(GLenum target, GLenum attachment, GLenum textarget, GLuint texture, GLint level);
using GlBlitFramebufferProcPtr = void (*)(GLint srcX0, GLint srcY0, GLint srcX1, GLint srcY1,
                                          GLint dstX0, GLint dstY0, GLint dstX1, GLint dstY1,
                                          GLbitfield mask, GLenum filter);
using GlCheckFramebufferStatusProcPtr = GLenum (*)(GLenum target);

// DSA (GL 4.5 ARB_direct_state_access)：直接按名字操作 FBO，不经过当前 binding。
using GlNamedFramebufferTextureProcPtr = void (*)(GLuint framebuffer, GLenum attachment, GLuint texture, GLint level);
using GlBlitNamedFramebufferProcPtr = void (*)(GLuint readFramebuffer, GLuint drawFramebuffer,
                                               GLint srcX0, GLint srcY0, GLint srcX1, GLint srcY1,
                                               GLint dstX0, GLint dstY0, GLint dstX1, GLint dstY1,
                                               GLbitfield mask, GLenum filter);

// 函数指针（extern，定义在 FboExtensions.cu）。必选 6 个 + 可选 2 个 DSA。
extern GlGenFramebuffersProcPtr s_glGenFramebuffers;
extern GlDeleteFramebuffersProcPtr s_glDeleteFramebuffers;
extern GlBindFramebufferProcPtr s_glBindFramebuffer;
extern GlFramebufferTexture2DProcPtr s_glFramebufferTexture2D;
extern GlBlitFramebufferProcPtr s_glBlitFramebuffer;
extern GlCheckFramebufferStatusProcPtr s_glCheckFramebufferStatus;
// DSA 指针可能为 NULL（context < 4.5），NULL 时调用方走 bind 回退。
extern GlNamedFramebufferTextureProcPtr s_glNamedFramebufferTexture;
extern GlBlitNamedFramebufferProcPtr s_glBlitNamedFramebuffer;

/**
 * 平台无关的 GL 扩展函数加载：把 wgl/glX 的平台差异收敛到这一处。
 */
void* getGlProc(const char* name);

/**
 * 加载 FBO/blit 必选指针（GL 3.0+）+ 可选 DSA 指针（GL 4.5）。
 * 必选失败抛 std::runtime_error；DSA 失败留 NULL（调用方走回退）。
 * 幂等：重复调用只加载一次。
 */
void initFboExtensions();
