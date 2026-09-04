#include "rgb_float_encoder_bridge.h"
#include <cuda_gl_interop.h>
#include "features/smarter/agent/reflex_arc_system_agent/sensor/possession_sensor/vision/cuda_gl_vision.h"
#include "core/containers/vector/Vector.h"
#include "core/containers/texture/Texture.h"
#include <iostream>

/**
 * RGB float 解码（从快照纹理到 FloatVector，CPU侧异步，GPU侧顺序执行）。
 *
 * 时间线（采集已由调用方先行完成，此处只做解码）：
 * [CUDA Stream]
 *     │
 *     ├─ cudaGraphicsMapResources
 *     │  (snapshot texture)
 *     ├─ Get Mapped Array
 *     ├─ Create Texture Object
 *     ├─ Launch Kernel
 *     │  (encode to FloatVector)
 *     │  ~5ms (async)
 *     ├─ Destroy TexObj
 *     └─ Unmap Resources
 *     │
 *     ◄── 返回给调用方 ──┘
 *         (CPU 侧立即返回)
 *
 * 后续：调用方在适当时机调用 cudaStreamSynchronize(stream)
 *       或依赖 implicit synchronization（如 copyToHost）
 */
cudaError_t rgbFloatEncodeBridge(
    Texture* snapshotTexture,
    size_t elementOffset,
    cudaStream_t stream,
    /* -> */ Vector<float>* dst_vector
)
{
    if (!dst_vector || !snapshotTexture) {
        return cudaErrorInvalidValue;
    }

    float* dstHandle = dst_vector->data();

    try {
        // ============================================================
        // 解码区域 = 整个 snapshotTexture（尺寸由纹理自取）
        // ============================================================
        // 采集侧（Texture::snapshotFrom）用 glBlitFramebuffer 把整源窗口
        // 缩放 blit 填满整个 snapshotTexture（固定 AI 视网膜分辨率），
        // 故解码区域 = snapshotTexture 全域，1:1 直读。
        int width  = snapshotTexture->getWidth();
        int height = snapshotTexture->getHeight();

        if (width <= 0 || height <= 0) {
            return cudaErrorInvalidValue;
        }

        // ============================================================
        // CUDA 解码（从快照纹理到 FloatVector）
        // ============================================================
        cudaGraphicsResource* cudaResource = snapshotTexture->getCudaResource();

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

        // 启动解码 Kernel（通道平面式 RGB float，[0,1] 归一化）
        float* dstPtr = dstHandle + elementOffset;

        err = launchRgbFloatKernel(texObj, width, height, stream, /* -> */ dstPtr);
        cudaDestroyTextureObject(texObj);

        if (err != cudaSuccess) {
            cudaGraphicsUnmapResources(1, &cudaResource, stream);
            return err;
        }

        // Unmap 资源（异步，插入到 stream 尾部）
        err = cudaGraphicsUnmapResources(1, &cudaResource, stream);

        // 返回（不调用 cudaStreamSynchronize！）：
        // - CUDA 队列：Map→Kernel→Unmap 已排队，按序执行
        // - CPU：立即返回，不阻塞
        return err;
    }
    catch (const std::exception& e) {
        std::cerr << "[Vision] Error in rgbFloatEncodeBridge: " << e.what() << std::endl;
        return cudaErrorUnknown;
    }
}
