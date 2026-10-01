// cuda_gl_vision.cu
// 视觉解码 kernel（新版分支）：快照纹理（GL 纹理）→ NN 家族载体。
// 位平面版服务 BNN 载体（BoolVector），RGB float 版服务 CNN 载体（FloatVector）。
// 参数序遵循设计原则第5条（DPS）：左边入参，右边出参，/* -> */ 分隔。
#include "cuda_gl_vision.h"

// 位平面 kernel: RGBA 纹理 → 24 个位平面（R0-R7, G0-G7, B0-B7），平面优先存储。
// Grid: y = 位平面索引 (0..23)，x = 平面内 word 索引。
__global__ void unpackRgbBitplanesKernel(
    cudaTextureObject_t texObj,
    int outWidth,
    int outHeight,
    int inWidth,
    int inHeight,
    int wordsPerPlane,
    /* -> */ uint32_t* __restrict__ out
)
{
    int planeIdx = blockIdx.y;
    int wordIdx  = blockIdx.x * blockDim.x + threadIdx.x;

    if (planeIdx >= 24 || wordIdx >= wordsPerPlane) return;

    int pixelBase = wordIdx * 32;
    int totalOutPixels = outWidth * outHeight;
    uint32_t word = 0;

    #pragma unroll
    for (int i = 0; i < 32; i++) {
        int pixelIdx = pixelBase + i;
        if (pixelIdx >= totalOutPixels) break;

        int x_out = pixelIdx % outWidth;
        int y_out = pixelIdx / outWidth;

        // 将输出视网膜坐标映射到输入纹理坐标
        // 使用像素中心对齐，配合 cudaFilterModePoint 实现最近邻缩放
        float x_in = (x_out + 0.5f) * inWidth  / (float)outWidth  - 0.5f;
        float y_in = (y_out + 0.5f) * inHeight / (float)outHeight - 0.5f;

        // 钳制到有效范围，防止浮点误差导致越界
        x_in = fmaxf(0.0f, fminf(x_in, inWidth  - 1.0f));
        y_in = fmaxf(0.0f, fminf(y_in, inHeight - 1.0f));

        // 硬件最近邻采样（因为 texDesc.filterMode = cudaFilterModePoint）
        uchar4 rgba = tex2D<uchar4>(texObj, x_in, y_in);

        int bit;
        if (planeIdx < 8) {
            bit = (rgba.x >> planeIdx) & 1;           // R0 .. R7
        } else if (planeIdx < 16) {
            bit = (rgba.y >> (planeIdx - 8)) & 1;     // G0 .. G7
        } else {
            bit = (rgba.z >> (planeIdx - 16)) & 1;    // B0 .. B7
        }

        word |= (bit << i);
    }

    // 平面优先存储
    out[planeIdx * wordsPerPlane + wordIdx] = word;
}

// RGB float kernel: RGBA 纹理 → 通道平面式 RGB float（R 平面 | G 平面 | B 平面），
// 值域 [0,1]（v/255 归一化）——CNN（浮点激活网络）可利用的数据类型。
// 布局与位平面编码器的平面优先约定一致；快照纹理恒为 AI 视网膜尺寸，1:1 直读。
// Grid: y = 通道索引 (0=R,1=G,2=B)，x = 像素索引。
__global__ void unpackRgbFloatKernel(
    cudaTextureObject_t texObj,
    int width,
    int height,
    /* -> */ float* __restrict__ out
)
{
    int channel = blockIdx.y;
    int pixelIdx = blockIdx.x * blockDim.x + threadIdx.x;
    int totalPixels = width * height;

    if (channel >= 3 || pixelIdx >= totalPixels) return;

    int x = pixelIdx % width;
    int y = pixelIdx / width;

    uchar4 rgba = tex2D<uchar4>(texObj, x, y);

    float v;
    if (channel == 0) {
        v = rgba.x;  // R
    } else if (channel == 1) {
        v = rgba.y;  // G
    } else {
        v = rgba.z;  // B
    }

    // 通道平面式：out[c * w * h + y * w + x]
    out[channel * totalPixels + pixelIdx] = v / 255.0f;
}

// 启动位平面解码 kernel（BNN 载体）。
// texObj:       从 cudaArray 创建的 texture object（整数坐标、最近邻采样）
// wordsPerPlane: 每平面 uint32_t 数 = ceil(width * height / 32)
// stream:       CUDA stream，0 表示默认流
cudaError_t launchUnpackKernel(
    cudaTextureObject_t texObj,
    int outWidth,
    int outHeight,
    int inWidth,
    int inHeight,
    int wordsPerPlane,
    cudaStream_t stream,
    /* -> */ uint32_t* out
)
{
    dim3 block(256);
    dim3 grid((wordsPerPlane + block.x - 1) / block.x, 24);

    unpackRgbBitplanesKernel<<<grid, block, 0, stream>>>(
        texObj, outWidth, outHeight, inWidth, inHeight, wordsPerPlane,
        /* -> */ out
    );

    return cudaGetLastError();
}

// 启动 RGB float 解码 kernel（CNN 载体）。
// texObj:  从 cudaArray 创建的 texture object（整数坐标、最近邻采样）
// stream:  CUDA stream，0 表示默认流
cudaError_t launchRgbFloatKernel(
    cudaTextureObject_t texObj,
    int width,
    int height,
    cudaStream_t stream,
    /* -> */ float* out
)
{
    int totalPixels = width * height;
    dim3 block(256);
    dim3 grid((totalPixels + block.x - 1) / block.x, 3);

    unpackRgbFloatKernel<<<grid, block, 0, stream>>>(
        texObj, width, height,
        /* -> */ out
    );

    return cudaGetLastError();
}
