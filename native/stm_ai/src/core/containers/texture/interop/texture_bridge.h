#pragma once
#include <cstddef>
#include <cstdint>

class Texture;

extern "C"
{
    /**
     * 创建 Texture 对象（分配 OpenGL 纹理 + 注册到 CUDA）。
     *
     * @param width  纹理宽度（像素）
     * @param height 纹理高度（像素）
     * @return Texture* 指针（失败返回 nullptr）
     */
    Texture* TextureCreate(int width, int height);

    /**
     * 销毁 Texture 对象（释放 OpenGL + CUDA 资源）。
     *
     * @param texture Texture 对象指针
     */
    void TextureDestroy(Texture* texture);

    /**
     * 获取 OpenGL 纹理 ID。
     */
    unsigned int TextureGetId(const Texture* texture);

    /**
     * 获取 CUDA 资源句柄。
     *
     * 注意：使用 intptr_t（64位）存储指针，而非 unsigned long long。
     * 对标 inference_ops_bridge.h 的规范：Windows x64 (LLP64 模型) 下
     * long 仅有 32 位，会导致指针截断。
     */
    intptr_t TextureGetCudaResourceHandle(const Texture* texture);

    /**
     * 获取纹理宽度。
     */
    int TextureGetWidth(const Texture* texture);

    /**
     * 获取纹理高度。
     */
    int TextureGetHeight(const Texture* texture);

    /**
     * 将源纹理快照到此纹理（GPU 侧异步拷贝）。
     *
     * 实际拷贝区域 = min(srcWidth, 纹理宽度) × min(srcHeight, 纹理高度)：
     * - 源 > 纹理：裁剪左上角（不越界）
     * - 源 < 纹理：只复制源实际尺寸（避免 GL_INVALID_VALUE）
     *
     * 采样分辨率由 Java 侧决定（传入源实际尺寸），C 侧不硬编码 1080p。
     *
     * @param texture       目标 Texture 对象
     * @param srcTextureId  源纹理的 OpenGL ID
     * @param srcWidth      源纹理实际宽度（像素，由 Java 侧传入）
     * @param srcHeight     源纹理实际高度（像素，由 Java 侧传入）
     */
    void TextureSnapshotFrom(Texture* texture, unsigned int srcTextureId, int srcWidth, int srcHeight);
}