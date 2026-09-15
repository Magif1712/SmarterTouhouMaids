#pragma once
#include <cuda_runtime.h>
#include <stdint.h>

// CNN-Active 权重对称初始化（D1/D3 的 native 实在化，方案 §7.1）。
//
// 与核心 fillRandomFloats 的唯一差异是"零均值"：
//   原核心（根因源头，不改）：data[i] = u01 * bound            ⟹ [0, +bound) 单边非负
//   本模块（D1）：            data[i] = (u01 * 2 - 1) * bound  ⟹ [−bound, +bound) 对称零均值
//
// 为什么必须对称而不是单边负（方案 §7.1）：单边负 [−B, 0] ⟹ z 系统性偏负 ⟹ 落"全 0 吸附点"；
// 全 0 时拮抗对做差 0 − 0 = 0 ⟹ 效应器同样不动。单边负只是把病态从一个吸附点平移到另一个。
//
// 施加对象：q / l / r（半径 boundW）与 b（半径 boundB）。**p 不施加**（p 是位置语义，
// 范围 [0, sizeA1)，对称化会把位置打到负数 ⟹ 结构性禁止，方案 §5 裁决2）。
//
// 设计原则（真善美第3条）：把"对称零均值"这个约束，用与核心 fillRandomFloats 对称的实在 kernel 固化。
// 入参在前，/* -> */ 之后是出参（设计原则第5条 DPS 方向标记）——data 为原地写入的出参。
extern "C" void cnn_active_fill_symmetric(
    int n, float bound, uint64_t seed, cudaStream_t stream /* -> */, float* data);
