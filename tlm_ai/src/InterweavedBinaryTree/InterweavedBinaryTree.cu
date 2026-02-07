#include "InterweavedBinaryTree.h"
#include <cuda_runtime.h>
#include <vector>
#include <algorithm>

namespace ibt {

struct DeviceHandle {
  uint64_t* d_keys = nullptr;
  uint32_t key_count = 0;
  uint32_t key_bits = 0;
  uint32_t levels = 0;
  uint32_t bits_a = 0;
  uint32_t bits_b = 0;
  uint64_t* d_bitset = nullptr;
  uint32_t bitset_words = 0;
  uint32_t row_bits = 0;
};

static inline uint32_t bitbuf_bit(const BitBuffer& buf, uint32_t idx_from_msb) {
  const uint32_t word = idx_from_msb / 64u;
  const uint32_t off = idx_from_msb % 64u;
  return (buf.words[word] >> (63u - off)) & 1u;
}

static uint64_t bitbuf_extract(const BitBuffer& buf, uint32_t bit_off, uint32_t bit_len) {
  uint64_t v = 0;
  for (uint32_t i = 0; i < bit_len; ++i) {
    v = (v << 1) | bitbuf_bit(buf, bit_off + i);
  }
  return v;
}

static void enumerate_keys64(const InterweavedBinaryTree& t, uint32_t node_idx,
                             uint64_t acc, uint32_t consumed,
                             std::vector<Key64>& out) {
  const auto& nodes = t.nodes();
  const auto& bitbuf = t.bit_buffer();
  const Node& n = nodes[node_idx];
  if (n.prefix.bit_len > 0) {
    const uint64_t seg = bitbuf_extract(bitbuf, n.prefix.bit_offset, n.prefix.bit_len);
    acc = (acc << n.prefix.bit_len) | seg;
    consumed += n.prefix.bit_len;
  }
  if (n.terminal) out.push_back(Key64{acc, consumed});
  if (n.child0 != 0xFFFFFFFFu) {
    enumerate_keys64(t, n.child0, (acc << 1), consumed + 1u, out);
  }
  if (n.child1 != 0xFFFFFFFFu) {
    enumerate_keys64(t, n.child1, (acc << 1) | 1u, consumed + 1u, out);
  }
}

extern "C" DeviceHandle* ibt_cuda_upload(const InterweavedBinaryTree* host) {
  if (!host) return nullptr;
  std::vector<Key64> keys;
  keys.reserve(host->nodes_count());
  if (host->nodes_count() > 0) enumerate_keys64(*host, 0u, 0ull, 0u, keys);
  std::sort(keys.begin(), keys.end(), [](const Key64& a, const Key64& b) {
    if (a.bits != b.bits) return a.bits < b.bits;
    return a.v < b.v;
  });

  auto* dev = new DeviceHandle{};
  dev->key_count = static_cast<uint32_t>(keys.size());
  dev->key_bits = host->spec().total_bits;
  dev->levels = host->spec().levels;
  if (dev->levels >= 1) dev->bits_a = host->spec().bits_per_level[0];
  if (dev->levels >= 2) dev->bits_b = host->spec().bits_per_level[1];

  if (dev->key_count > 0) {
    cudaMalloc(reinterpret_cast<void**>(&dev->d_keys), sizeof(uint64_t) * dev->key_count);
    std::vector<uint64_t> raw;
    raw.reserve(dev->key_count);
    for (const auto& k : keys) raw.push_back(k.v);
    cudaMemcpy(dev->d_keys, raw.data(), sizeof(uint64_t) * dev->key_count, cudaMemcpyHostToDevice);
  }
  return dev;
}

extern "C" void ibt_cuda_release(DeviceHandle* dev) {
  if (!dev) return;
  if (dev->d_keys) cudaFree(dev->d_keys);
  if (dev->d_bitset) cudaFree(dev->d_bitset);
  delete dev;
}

__device__ static inline uint32_t key_bit64_dev(uint64_t key, uint32_t key_len, uint32_t idx_from_msb) {
  return (key >> (key_len - 1u - idx_from_msb)) & 1u;
}

__device__ static bool vec_contains(const uint64_t* vec, uint32_t count, uint64_t key) {
  uint32_t l = 0, r = count;
  while (l < r) {
    const uint32_t m = (l + r) >> 1;
    const uint64_t v = vec[m];
    if (v == key) return true;
    if (v < key) l = m + 1; else r = m;
  }
  return false;
}

__global__ static void matvec_or_kernel(const uint64_t* matrix_keys, uint32_t matrix_count,
                                        const uint64_t* vector_keys, uint32_t vector_count,
                                        uint32_t bits_row, uint32_t bits_col,
                                        uint64_t* out_bitset) {
  const uint32_t idx = blockIdx.x * blockDim.x + threadIdx.x;
  if (idx >= matrix_count) return;
  const uint64_t key = matrix_keys[idx];
  uint64_t row = 0, col = 0;
  uint32_t pos = 0;
  const uint32_t max_plane = max(bits_row, bits_col);
  for (uint32_t plane = 0; plane < max_plane; ++plane) {
    if (plane < bits_row) {
      const uint32_t bit = key_bit64_dev(key, bits_row + bits_col, pos);
      row = (row << 1) | bit;
      ++pos;
    }
    if (plane < bits_col) {
      const uint32_t bit = key_bit64_dev(key, bits_row + bits_col, pos);
      col = (col << 1) | bit;
      ++pos;
    }
  }
  if (vec_contains(vector_keys, vector_count, col)) {
    const uint64_t word = row >> 6;
    const uint64_t bit = row & 63u;
    atomicOr(&out_bitset[word], (uint64_t(1) << bit));
  }
}

extern "C" void ibt_cuda_matvec_or(DeviceHandle* d_matrix, DeviceHandle* d_vector, DeviceHandle*& d_out) {
  if (!d_matrix || !d_vector) return;
  if (d_matrix->levels != 2 || d_vector->levels != 1) return;
  const uint32_t bits_row = d_matrix->bits_a;
  const uint32_t bits_col = d_matrix->bits_b;
  const uint64_t rows = uint64_t(1) << bits_row;
  const uint32_t words = static_cast<uint32_t>((rows + 63u) >> 6);
  auto* out = new DeviceHandle{};
  out->row_bits = bits_row;
  out->bitset_words = words;
  cudaMalloc(reinterpret_cast<void**>(&out->d_bitset), sizeof(uint64_t) * words);
  cudaMemset(out->d_bitset, 0, sizeof(uint64_t) * words);
  const uint32_t threads = 256;
  const uint32_t blocks = (d_matrix->key_count + threads - 1) / threads;
  matvec_or_kernel<<<blocks, threads>>>(d_matrix->d_keys, d_matrix->key_count,
                                        d_vector->d_keys, d_vector->key_count,
                                        bits_row, bits_col,
                                        out->d_bitset);
  d_out = out;
}

extern "C" void ibt_cuda_download_keys(DeviceHandle* d_out, std::vector<Key64>& keys) {
  if (!d_out || !d_out->d_bitset) return;
  std::vector<uint64_t> host_bits(d_out->bitset_words);
  cudaMemcpy(host_bits.data(), d_out->d_bitset, sizeof(uint64_t) * d_out->bitset_words, cudaMemcpyDeviceToHost);
  const uint64_t rows = uint64_t(1) << d_out->row_bits;
  keys.clear();
  for (uint64_t i = 0; i < rows; ++i) {
    const uint64_t word = i >> 6;
    const uint64_t bit = i & 63u;
    if (host_bits[word] & (uint64_t(1) << bit)) {
      keys.push_back(Key64{static_cast<uint64_t>(i), d_out->row_bits});
    }
  }
}

} // namespace ibt
