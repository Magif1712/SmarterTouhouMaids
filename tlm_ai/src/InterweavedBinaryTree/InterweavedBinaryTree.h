#pragma once
// 交织二叉树 CPU/GPU 同构接口（C++17 / CUDA）
// 面向超大维度二进制张量的稀疏表示与前缀查询、矩阵-向量乘法
// 设计要点：
// - 采用位交织编码（高位到低位分层交织）将多维索引折叠为单一比特串
// - 压缩二叉前缀树（Patricia Trie）：节点携带一段公共前缀，减少节点数量与内存占用
// - 扁平化存储结构，child 索引使用 32-bit，相邻布局利于 CPU/GPU 访问
// - 提供 CPU 侧构建/查询/乘法，及 GPU 侧数据上传与并行乘法入口
//
// 限制与约定：
// - 总比特数 <= 64 时走快速路径 uint64_t；>64 时使用 {hi,lo} 双 64 位表示
// - 典型矩阵使用 L=2（行、列），向量使用 L=1；乘法采用“存在性（OR）聚合”
// - 前缀比较按 MSB→LSB 顺序，交织顺序为每层按位循环交织
//
// 使用示例见同目录 使用说明.txt

#include <cstdint>
#include <vector>
#include <utility>
#include <algorithm>
#include <atomic>

namespace ibt {

struct BitsSpec {
  // 每层的位数，按 MSB→LSB 顺序交织
  std::vector<uint32_t> bits_per_level;
  uint32_t levels = 0;
  uint32_t total_bits = 0;
};

struct Key64 {
  uint64_t v = 0;
  uint32_t bits = 0;
};

struct Key128 {
  uint64_t hi = 0;
  uint64_t lo = 0;
  uint32_t bits = 0;
};

// 前缀段缓冲区（位序 MSB→LSB）
struct PrefixSlice {
  // 在位缓冲区中的起始位偏移（相对于 bit_buffer 中的 MSB 坐标）
  uint32_t bit_offset = 0;
  // 前缀段长度（位数）
  uint16_t bit_len = 0;
};

// 压缩节点（Patricia Trie）
struct Node {
  // 公共前缀段描述
  PrefixSlice prefix{};
  // 子节点索引（0/1 分支），0xFFFFFFFF 表示不存在
  uint32_t child0 = 0xFFFFFFFFu;
  uint32_t child1 = 0xFFFFFFFFu;
  // 是否在此节点终止一个元素（用于恰好在前缀结束处的键）
  uint8_t terminal = 0;
  // 对于 L=2 的矩阵场景，terminal_row_id 可记录行索引（可选）
  uint32_t terminal_row_id = 0xFFFFFFFFu;
};

// 扁平化前缀位缓冲区，按 64-bit 分块存储，便于 GPU 读取
struct BitBuffer {
  std::vector<uint64_t> words;   // 逻辑上为连续位数组，words[0] 的最高位为全局 MSB
  uint32_t total_bits = 0;
};

// 设备侧句柄（延迟定义，避免包含 CUDA 头）
struct DeviceHandle;

class InterweavedBinaryTree {
public:
  InterweavedBinaryTree() = default;
  explicit InterweavedBinaryTree(const BitsSpec& spec);

  const BitsSpec& spec() const { return spec_; }
  uint32_t nodes_count() const { return static_cast<uint32_t>(nodes_.size()); }

  // 索引位交织（输入为每层的无符号索引，按 bits_per_level 最高位对齐）
  // 总位数 <= 64 走 64 位快捷编码，返回 Key64；否则返回 Key128
  Key64 encode64(const std::vector<uint64_t>& per_level_indices) const;
  Key128 encode128(const std::vector<uint64_t>& per_level_indices) const;

  // 批量构建（输入必须按交织编码升序排序，可显著减少分配与分支）
  void build_from_sorted_keys64(const std::vector<Key64>& sorted_keys);
  void build_from_sorted_keys128(const std::vector<Key128>& sorted_keys);

  // 单键插入（内部会进行必要的节点拆分与前缀对齐）
  void insert64(const Key64& k);
  void insert128(const Key128& k);

  // 成员查询
  bool contains64(const Key64& k) const;
  bool contains128(const Key128& k) const;

  // 向量前缀存在性查询（用于乘法剪枝），prefix_bits 为已知高位前缀
  bool vector_prefix_exists64(uint64_t prefix, uint32_t prefix_len) const;
  bool vector_prefix_exists128(const Key128& prefix, uint32_t prefix_len) const;

  // 矩阵-向量乘法（OR 聚合）：matrix = L=2, vector = L=1, 返回输出向量（L=1）
  // CPU 版本：适合中等稀疏度；GPU 版本：自动上传并并行执行
  static InterweavedBinaryTree matvec_or_cpu(const InterweavedBinaryTree& matrix,
                                             const InterweavedBinaryTree& vector);
  static InterweavedBinaryTree matvec_or_gpu(const InterweavedBinaryTree& matrix,
                                             const InterweavedBinaryTree& vector);

  // 设备上传与释放（供 GPU 乘法使用）
  void upload_to_device(DeviceHandle*& dev) const;
  static void release_device(DeviceHandle*& dev);

  // 只读访问（用于调试或外部遍历）
  const std::vector<Node>& nodes() const { return nodes_; }
  const BitBuffer& bit_buffer() const { return bitbuf_; }

private:
  BitsSpec spec_{};
  std::vector<Node> nodes_;
  BitBuffer bitbuf_;

  // 内部工具：向位缓冲追加一段比特，返回其起始偏移
  uint32_t append_bits64(uint64_t bits, uint32_t bit_len);
  // 比较键从 start 开始与节点前缀段的匹配位数
  uint32_t match_prefix64(uint64_t key, uint32_t key_len, uint32_t start, const Node& n) const;
  // 取键某一位（MSB 基准）
  static inline uint32_t get_bit64(uint64_t v, uint32_t idx_from_msb) {
    return (v >> (63u - idx_from_msb)) & 1u;
  }
  static inline uint64_t mask_msb64(uint32_t len) {
    return len == 64 ? ~uint64_t(0) : ((uint64_t(1) << len) - 1) << (64 - len);
  }

  // 插入/查询核心（64 位）
  void insert_core64(uint64_t key, uint32_t key_len, uint32_t* out_row_opt);
  bool contains_core64(uint64_t key, uint32_t key_len) const;

  // 构建辅助：从已排序键批量生成节点
  void build_from_sorted64_impl(const std::vector<Key64>& keys, uint32_t l, uint32_t r,
                                uint32_t bit_pos_from_msb, uint32_t parent_idx);

  // GPU 相关（由 .cu 实现）
  friend struct DeviceHandle;
};

} // namespace ibt

