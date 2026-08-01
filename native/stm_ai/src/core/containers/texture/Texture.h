#pragma once

// Windows 上 GL/gl.h 依赖 windows.h 定义的 APIENTRY/WINGDIAPI 等宏
// 否则 GLuint/GLEnum 不会被 typedef，cuda_gl_interop.h 会炸
#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#endif

#include <GL/gl.h>

// GL_CLAMP_TO_EDGE 在某些 OpenGL 实现中可能需要额外定义
#ifndef GL_CLAMP_TO_EDGE
#define GL_CLAMP_TO_EDGE 0x812F
#endif

#include <cuda_gl_interop.h>

/**
 * GPU 纹理资源容器（RGBA8 格式），封装 OpenGL 纹理及其 CUDA 互操作注册。
 * <p>
 * 设计原则（对标 Vector<T> 的 RAII 模式）：
 * - 构造时分配资源（glGenTextures + cudaGraphicsGLRegisterImage + FBO 对）
 * - 析构时释放资源（FBO + cudaGraphicsUnregisterResource + glDeleteTextures）
 * - 禁止拷贝（=delete），仅允许移动语义
 */
class Texture {
public:
    Texture(int width, int height);
    ~Texture();

    Texture(const Texture&) = delete;
    Texture& operator=(const Texture&) = delete;
    Texture(Texture&& other) noexcept;
    Texture& operator=(Texture&& other) noexcept;

    GLuint getTextureId() const { return m_textureId; }
    cudaGraphicsResource* getCudaResource() const { return m_cudaResource; }
    int getWidth() const { return m_width; }
    int getHeight() const { return m_height; }

    /**
     * 把源纹理的内容缩放 blit 到本纹理（填满 m_width × m_height）。
     *
     * 用 glBlitFramebuffer 替代旧的 glCopyImageSubData：
     * - 旧方案 1:1 拷贝，源 > 本纹理时只拷左下角（裁剪），>1080p 窗口只看到左下角。
     * - 新方案整源 (srcWidth × srcHeight) → 整目标 (m_width × m_height)，GL_NEAREST
     *   跨步抽样，任意窗口分辨率都能完整缩放到固定视网膜分辨率。
     *
     * 同走 OpenGL 命令队列：按序执行、防止源被中途改写（防错乱）、不阻塞 CPU。
     * GL_NEAREST 不混合 RGB，保护 bit-plane 提取完整性。
     * 保存/恢复宿主 FBO 绑定，不污染渲染线程的 GL 状态。
     */
    void snapshotFrom(GLuint srcTextureId, int srcWidth, int srcHeight);

private:
    GLuint m_textureId = 0;
    cudaGraphicsResource* m_cudaResource = nullptr;
    int m_width = 0;
    int m_height = 0;
    // FBO 对：read 挂外部源纹理（每帧重挂），draw 永久挂本纹理。
    GLuint m_readFbo = 0;
    GLuint m_drawFbo = 0;

    void release() noexcept;
};
