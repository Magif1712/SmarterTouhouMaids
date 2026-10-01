#pragma once
#include <cstdint>

// CNN-Active 前向 bridge（参数全用 intptr_t，Windows x64 LLP64 安全）。
// p 句柄接收但前向不用（p 仅 refreshCache/backward 用），bridge 不转发给 host。
// prevB 句柄是本 NN 自持的"上一拍行为段"（256 float）；activationId 是 D2 的显式入参。
// traceZ==0 走 NoTrace 路径，!=0 走 StoreTrace 路径；traceY 统一为"kernel 写入的输出缓冲区"。
void cnn_active_forward_layer_bridge(
    intptr_t x, intptr_t p, intptr_t q, intptr_t l, intptr_t r, intptr_t b,
    intptr_t idx0, intptr_t idx1, intptr_t w0, intptr_t w1,
    intptr_t prevB, int activationId,
    int sizeA0, int sizeA1, intptr_t stream /* -> */,
    intptr_t traceZ, intptr_t traceY);

// CNN-Active 缓存刷新 bridge（idx/w 是本模块自有的 p 派生缓存，不与原 CNN 共享生产者）。
void cnn_active_refresh_cache_bridge(
    intptr_t p, int sizeA0, int sizeA1, intptr_t stream /* -> */,
    intptr_t idx0, intptr_t idx1, intptr_t w0, intptr_t w1);
