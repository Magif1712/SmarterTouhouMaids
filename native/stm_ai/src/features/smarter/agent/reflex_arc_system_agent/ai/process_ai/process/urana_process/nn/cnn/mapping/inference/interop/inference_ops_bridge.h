#pragma once
#include <cstdint>

// CNN 前向 bridge（参数全用 intptr_t，Windows x64 LLP64 安全）。
// p 句柄接收但前向不用（p 仅 refreshCache/backward 用），bridge 不转发给 host。
// traceZ==0 走 NoTrace 路径，!=0 走 StoreTrace 路径。
void cnn_forward_layer_bridge(
    intptr_t x, intptr_t p, intptr_t q, intptr_t l, intptr_t r, intptr_t b,
    intptr_t idx0, intptr_t idx1, intptr_t w0, intptr_t w1,
    int sizeA0, int sizeA1, intptr_t stream /* -> */,
    intptr_t y, intptr_t traceZ, intptr_t traceY);

// CNN 缓存刷新 bridge。
void cnn_refresh_cache_bridge(
    intptr_t p, int sizeA0, int sizeA1, intptr_t stream /* -> */,
    intptr_t idx0, intptr_t idx1, intptr_t w0, intptr_t w1);
