// cuda_gl_vision.cu 
 #include "cuda_gl_vision.h" 
 
 __global__ void unpackRgbBitplanesKernel(
    cudaTextureObject_t texObj,
    uint32_t* __restrict__ out,
    int outWidth,
    int outHeight,
    int inWidth,
    int inHeight,
    int wordsPerPlane
)
{
    // Grid: y = 位平面索引 (0..23)，x = 平面内 word 索引
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

cudaError_t launchUnpackKernel(
    cudaTextureObject_t texObj,
    uint32_t* out,
    int outWidth,
    int outHeight,
    int inWidth,
    int inHeight,
    int wordsPerPlane,
    cudaStream_t stream
)
{
    dim3 block(256);
    dim3 grid((wordsPerPlane + block.x - 1) / block.x, 24);

    unpackRgbBitplanesKernel<<<grid, block, 0, stream>>>(
        texObj, out, outWidth, outHeight, inWidth, inHeight, wordsPerPlane
    );

    return cudaGetLastError();
}