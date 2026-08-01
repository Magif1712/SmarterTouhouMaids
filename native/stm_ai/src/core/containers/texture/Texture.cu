#include "Texture.h"
#include "fbo_extensions/FboExtensions.h"
#include <stdexcept>
#include <iostream>
#include <string>

Texture::Texture(int width, int height)
    : m_width(width), m_height(height)
{
    if (width <= 0 || height <= 0) {
        throw std::invalid_argument("Invalid texture dimensions: " + std::to_string(width) + "x" + std::to_string(height));
    }

    glGenTextures(1, &m_textureId);
    glBindTexture(GL_TEXTURE_2D, m_textureId);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_2D, 0);

    cudaError_t err = cudaGraphicsGLRegisterImage(
        &m_cudaResource,
        m_textureId,
        GL_TEXTURE_2D,
        cudaGraphicsRegisterFlagsReadOnly
    );

    if (err != cudaSuccess) {
        release();
        throw std::runtime_error("cudaGraphicsGLRegisterImage failed: " + std::string(cudaGetErrorString(err)));
    }

    // 创建 FBO 对：read FBO（每帧挂外部源纹理）、draw FBO（永久挂本纹理）。
    // 用于 glBlitNamedFramebuffer / glBlitFramebuffer 把任意分辨率的源缩放 blit 到固定大小的本纹理。
    initFboExtensions();

    s_glGenFramebuffers(1, &m_readFbo);
    s_glGenFramebuffers(1, &m_drawFbo);

    // 先 bind 一次让两个 FBO 对象被真正创建（OpenGL 的 lazy creation：
    // glGenFramebuffers 只分配名字，对象在第一次 bind 时才初始化）。
    // DSA 函数（glNamedFramebufferTexture / glBlitNamedFramebuffer）要求传入的
    // FBO 是 valid object，未 bind 过的名字会被拒绝（GL_INVALID_OPERATION）。
    // readFBO 只需 touch 一下完成初始化（attachment 由 snapshotFrom 每帧用 DSA 挂）；
    // drawFBO 顺带永久挂本纹理。
    s_glBindFramebuffer(GL_FRAMEBUFFER, m_readFbo);
    s_glBindFramebuffer(GL_FRAMEBUFFER, m_drawFbo);
    s_glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, m_textureId, 0);
    if (s_glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
        s_glBindFramebuffer(GL_FRAMEBUFFER, 0);
        release();
        throw std::runtime_error("Draw framebuffer incomplete");
    }
    s_glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

Texture::~Texture() {
    release();
}

Texture::Texture(Texture&& other) noexcept
    : m_textureId(other.m_textureId)
    , m_cudaResource(other.m_cudaResource)
    , m_width(other.m_width)
    , m_height(other.m_height)
    , m_readFbo(other.m_readFbo)
    , m_drawFbo(other.m_drawFbo)
{
    other.m_textureId = 0;
    other.m_cudaResource = nullptr;
    other.m_width = 0;
    other.m_height = 0;
    other.m_readFbo = 0;
    other.m_drawFbo = 0;
}

Texture& Texture::operator=(Texture&& other) noexcept {
    if (this != &other) {
        release();
        m_textureId = other.m_textureId;
        m_cudaResource = other.m_cudaResource;
        m_width = other.m_width;
        m_height = other.m_height;
        m_readFbo = other.m_readFbo;
        m_drawFbo = other.m_drawFbo;
        other.m_textureId = 0;
        other.m_cudaResource = nullptr;
        other.m_width = 0;
        other.m_height = 0;
        other.m_readFbo = 0;
        other.m_drawFbo = 0;
    }
    return *this;
}

void Texture::snapshotFrom(GLuint srcTextureId, int srcWidth, int srcHeight) {
    if (!m_cudaResource) {
        throw std::logic_error("Cannot snapshot to uninitialized texture");
    }
    if (srcTextureId == 0) {
        throw std::invalid_argument("Invalid source texture ID");
    }
    if (srcWidth <= 0 || srcHeight <= 0) {
        throw std::invalid_argument("Invalid source dimensions: " + std::to_string(srcWidth) + "x" + std::to_string(srcHeight));
    }

    initFboExtensions();

    // 缩放 blit：整源 (srcWidth × srcHeight) → 整目标 (m_width × m_height)
    // GL_NEAREST = 跨步抽样（每 N 个源像素抽一个），不混合 RGB → 保护 bit-plane 提取。
    if (s_glNamedFramebufferTexture && s_glBlitNamedFramebuffer) {
        // DSA 路径（GL 4.5+，本 mod 用户 100% 可用）：
        // 直接按名字操作 FBO，不 bind、不查询、不恢复 → 零状态污染、零 GL_INVALID_ENUM。
        s_glNamedFramebufferTexture(m_readFbo, GL_COLOR_ATTACHMENT0, srcTextureId, 0);
        s_glBlitNamedFramebuffer(
            m_readFbo, m_drawFbo,
            0, 0, srcWidth, srcHeight,
            0, 0, m_width, m_height,
            GL_COLOR_BUFFER_BIT, GL_NEAREST
        );
    } else {
        // 回退路径（context < 4.5，理论边缘情况）：
        // 不查询宿主 binding（1.1 glGetIntegerv 查 3.0 枚举会报 GL_INVALID_ENUM），
        // blit 后直接 unbind 回 default(0)，read+draw 同时恢复。
        s_glBindFramebuffer(GL_READ_FRAMEBUFFER, m_readFbo);
        s_glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, srcTextureId, 0);
        s_glBindFramebuffer(GL_DRAW_FRAMEBUFFER, m_drawFbo);
        s_glBlitFramebuffer(
            0, 0, srcWidth, srcHeight,
            0, 0, m_width, m_height,
            GL_COLOR_BUFFER_BIT, GL_NEAREST
        );
        s_glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
}

void Texture::release() noexcept {
    // s_glDeleteFramebuffers 仅在 FBO 已创建（initFboExtensions 已跑过）时可用；
    // 若 FBO 句柄非 0，则对应指针必已加载。
    if (m_readFbo != 0 && s_glDeleteFramebuffers) {
        s_glDeleteFramebuffers(1, &m_readFbo);
        m_readFbo = 0;
    }
    if (m_drawFbo != 0 && s_glDeleteFramebuffers) {
        s_glDeleteFramebuffers(1, &m_drawFbo);
        m_drawFbo = 0;
    }

    if (m_cudaResource) {
        cudaGraphicsUnregisterResource(m_cudaResource);
        m_cudaResource = nullptr;
    }

    if (m_textureId != 0) {
        glDeleteTextures(1, &m_textureId);
        m_textureId = 0;
    }
}
