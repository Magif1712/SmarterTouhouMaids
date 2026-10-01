#include "vision_bridge.h"
#include <cuda_gl_interop.h>
#include "../cuda_gl_vision.h"
#include "core/containers/vector/Vector.h"
#include "core/containers/texture/Texture.h"
#include <unordered_map>
#include <iostream>

struct CachedResource {
    cudaGraphicsResource* resource = nullptr;
};

static std::unordered_map<GLuint, CachedResource> g_resourceCache;

/**
 * 通过快照机制捕获屏幕到 BoolVector（CPU侧异步，GPU侧顺序执行）。
 *
 * 时间线：
 * [OpenGL Thread/Ctx]          [CUDA Stream]
 *     │                              │
 *     ├─ glBlitFramebuffer          │
 *     │  (src → temp texture,缩放)  │
 *     │  ~0.1ms                     │
 *     │                              ├─ cudaGraphicsMapResources
 *     │                              │  (temp texture)
 *     │                              ├─ Get Mapped Array
 *     │                              ├─ Create Texture Object
 *     │                              ├─ Launch Kernel
 *     │                              │  (encode to BoolVector)
 *     │                              │  ~5ms (async)
 *     │                              ├─ Destroy TexObj
 *     │                              └─ Unmap Resources
 *     │                              │
 *     ◄──── 返回给调用方 ────────────┘
 *           (CPU 侧立即返回)
 *
 * 后续：调用方在适当时机调用 cudaStreamSynchronize(stream)
 *       或依赖 implicit synchronization（如 copyToHost）
 */
cudaError_t captureScreenViaSnapshotOriginalBridge(
    GLuint srcTextureId,
    Texture* tempTexture,
    Vector<bool>* dst_vector,
    size_t bitOffset,
    int texWidth,
    int texHeight,
    int aiWidth,
    int aiHeight,
    cudaStream_t stream)
{
    if (!dst_vector || !tempTexture) {
        return cudaErrorInvalidValue;
    }

    uint32_t* dstHandle = dst_vector->data();

    if (bitOffset % 32 != 0) {
        return cudaErrorInvalidValue;
    }
    if (aiWidth <= 0 || aiHeight <= 0) {
        return cudaErrorInvalidValue;
    }

    try {
        // ============================================================
        // Step 1: 快照（使用传入的池化 Texture 对象）
        // 传入源纹理实际尺寸（texWidth × texHeight），由 Texture::snapshotFrom
        // 用 glBlitFramebuffer 把整源 (texWidth × texHeight) 缩放 blit 填满
        // tempTexture（固定 AI 视网膜分辨率），GL_NEAREST 跨步抽样，不硬编码任何分辨率。
        // ============================================================
        tempTexture->snapshotFrom(srcTextureId, texWidth, texHeight);

        // ============================================================
        // Step 2: 采样区域 = 整个 tempTexture
        // ============================================================
        // snapshotFrom 现在用 glBlitFramebuffer 把整窗口 (texWidth × texHeight)
        // 缩放 blit 填满整个 tempTexture（固定 AI 视网膜分辨率），GL_NEAREST 跨步抽样。
        // 因此有效采样区域 = tempTexture 全域，不再 min 裁剪：
        //   - 源 > tempTexture (2K→1080p)：blit 降采样（跨步），整窗口可见
        //   - 源 = tempTexture (1080p)：1:1
        //   - 源 < tempTexture (720p→1080p)：blit 升采样（像素复制），整窗口可见
        // kernel 用 sampleWidth/sampleHeight 作为 inWidth/inHeight；因 tempTexture 按
        // AI 视网膜尺寸创建，inWidth=outWidth → 1:1。
        int sampleWidth  = tempTexture->getWidth();
        int sampleHeight = tempTexture->getHeight();

        // ============================================================
        // Step 3: CUDA 编码（从临时纹理到 BoolVector）
        // ============================================================
        cudaGraphicsResource* cudaResource = tempTexture->getCudaResource();

        cudaError_t err = cudaGraphicsMapResources(1, &cudaResource, stream);
        if (err != cudaSuccess) return err;

        cudaArray_t cuArray;
        err = cudaGraphicsSubResourceGetMappedArray(&cuArray, cudaResource, 0, 0);
        if (err != cudaSuccess) {
            cudaGraphicsUnmapResources(1, &cudaResource, stream);
            return err;
        }

        // 创建 CUDA Texture Object
        cudaResourceDesc resDesc = {};
        resDesc.resType = cudaResourceTypeArray;
        resDesc.res.array.array = cuArray;

        cudaTextureDesc texDesc = {};
        texDesc.addressMode[0] = cudaAddressModeClamp;
        texDesc.addressMode[1] = cudaAddressModeClamp;
        texDesc.filterMode = cudaFilterModePoint;
        texDesc.readMode = cudaReadModeElementType;
        texDesc.normalizedCoords = 0;

        cudaTextureObject_t texObj = 0;
        err = cudaCreateTextureObject(&texObj, &resDesc, &texDesc, nullptr);
        if (err != cudaSuccess) {
            cudaGraphicsUnmapResources(1, &cudaResource, stream);
            return err;
        }

        // 启动编码 Kernel
        // 使用 sampleWidth/sampleHeight（实际有效区域）而非 texWidth/texHeight（源尺寸）
        uint32_t* dstPtr = dstHandle + (bitOffset / 32);
        int wordsPerPlane = (aiWidth * aiHeight + 31) / 32;

        err = launchUnpackKernelOriginal(texObj, dstPtr, aiWidth, aiHeight, sampleWidth, sampleHeight, wordsPerPlane, stream);
        cudaDestroyTextureObject(texObj);

        if (err != cudaSuccess) {
            cudaGraphicsUnmapResources(1, &cudaResource, stream);
            return err;
        }

        // Unmap 资源（异步，插入到 stream 尾部）
        err = cudaGraphicsUnmapResources(1, &cudaResource, stream);

        // ============================================================
        // Step 3: 返回（不调用 cudaStreamSynchronize！）✨
        // ============================================================
        // 此时：
        // - OpenGL 队列：glBlitFramebuffer 可能还在执行或已完成
        // - CUDA 队列：Map→Kernel→Unmap 已排队，按序执行
        // - CPU：立即返回，不阻塞

        return err;
    }
    catch (const std::exception& e) {
        std::cerr << "[Vision] Error in captureScreenViaSnapshotOriginalBridge: " << e.what() << std::endl;
        return cudaErrorUnknown;
    }
}

void cleanupVisionOriginalBridge() {
    for (auto const& [key, val] : g_resourceCache) {
        cudaGraphicsUnregisterResource(val.resource);
    }
    g_resourceCache.clear();
}