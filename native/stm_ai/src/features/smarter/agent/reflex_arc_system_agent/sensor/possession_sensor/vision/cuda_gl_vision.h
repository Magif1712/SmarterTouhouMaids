// cuda_gl_vision.h 
 #pragma once 
 
 #include <cuda_runtime.h> 
 #include <stdint.h> 
 
 #ifdef __cplusplus 
 extern "C" { 
 #endif 
 
 // 启动内核：RGBA 纹理 → 24 个位平面（R0-R7, G0-G7, B0-B7） 
 // texObj:     从 cudaArray 创建的 texture object（整数坐标、最近邻采样） 
 // out:        BoolVector device data (uint32_t*)，已按 32 位 word 对齐 
 // wordsPerPlane: 每平面 uint32_t 数 = ceil(width * height / 32) 
 // stream:     CUDA stream，0 表示默认流 
 cudaError_t launchUnpackKernel(
    cudaTextureObject_t texObj,
    uint32_t* out,
    int outWidth,
    int outHeight,
    int inWidth,
    int inHeight,
    int wordsPerPlane,
    cudaStream_t stream
); 
 
 #ifdef __cplusplus 
 } 
 #endif