#include "InterweavedBinaryTree.h"
#include <cassert>
#include <stack>

namespace ibt {

struct DeviceHandle;
extern "C" DeviceHandle* ibt_cuda_upload(const InterweavedBinaryTree* host);
extern "C" void ibt_cuda_release(DeviceHandle* dev);
extern "C" void ibt_cuda_matvec_or(DeviceHandle* d_matrix, DeviceHandle* d_vector, DeviceHandle*& d_out);
extern "C" void ibt_cuda_download_keys(DeviceHandle* d_out, std::vector<Key64>& keys);

static BitsSpec make_spec_checked(const BitsSpec& in) {
  BitsSpec s = in;
  s.levels = static_cast<uint32_t>(s.bits_per_level.size());
  s.total_bits = 0;
  for (auto b : s.bits_per_level) s.total_bits += b;
  return s;
}

static BitsSpec make_spec_from_bits(const std::vector<uint32_t>& bits) {
  BitsSpec s{};
  s.bits_per_level = bits;
  s.levels = static_cast<uint32_t>(bits.size());
  s.total_bits = 0;
  for (auto b : bits) s.total_bits += b;
  return s;
}

static inline uint32_t key_bit64(uint64_t key, uint32_t key_len, uint32_t idx_from_msb) {
  return (key >> (key_len - 1u - idx_from_msb)) & 1u;
}

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

static uint64_t key_slice64(uint64_t key, uint32_t key_len, uint32_t start, uint32_t len) {
  if (len == 0) return 0;
  return (key >> (key_len - (start + len))) & ((uint64_t(1) << len) - 1u);
}

InterweavedBinaryTree::InterweavedBinaryTree(const BitsSpec& spec)
  : spec_(make_spec_checked(spec)) {
  nodes_.reserve(1024);
  bitbuf_.words.reserve(256);
}

Key64 InterweavedBinaryTree::encode64(const std::vector<uint64_t>& idx) const {
  Key64 out{};
  out.bits = spec_.total_bits;
  if (spec_.total_bits == 0) return out;
  uint32_t max_plane = 0;
  for (auto b : spec_.bits_per_level) max_plane = std::max(max_plane, b);
  uint64_t v = 0;
  uint32_t produced = 0;
  for (uint32_t plane = 0; plane < max_plane; ++plane) {
    for (uint32_t l = 0; l < spec_.levels; ++l) {
      const uint32_t bits_l = spec_.bits_per_level[l];
      if (plane < bits_l) {
        const uint32_t src_bit = bits_l - 1u - plane;
        const uint32_t bit = (idx[l] >> src_bit) & 1u;
        v = (v << 1) | bit;
        ++produced;
      }
    }
  }
  if (produced != spec_.total_bits) {
    out.bits = 0;
    out.v = 0;
    return out;
  }
  out.v = v;
  return out;
}

Key128 InterweavedBinaryTree::encode128(const std::vector<uint64_t>& idx) const {
  Key128 out{};
  out.bits = spec_.total_bits;
  if (spec_.total_bits == 0) return out;
  uint32_t max_plane = 0;
  for (auto b : spec_.bits_per_level) max_plane = std::max(max_plane, b);
  uint64_t hi = 0, lo = 0;
  uint32_t produced = 0;
  for (uint32_t plane = 0; plane < max_plane; ++plane) {
    for (uint32_t l = 0; l < spec_.levels; ++l) {
      const uint32_t bits_l = spec_.bits_per_level[l];
      if (plane < bits_l) {
        const uint32_t src_bit = bits_l - 1u - plane;
        const uint32_t bit = (idx[l] >> src_bit) & 1u;
        if (produced < 64) hi = (hi << 1) | bit;
        else lo = (lo << 1) | bit;
        ++produced;
      }
    }
  }
  if (produced != spec_.total_bits) {
    out.bits = 0; out.hi = 0; out.lo = 0;
    return out;
  }
  out.hi = hi; out.lo = lo;
  return out;
}

uint32_t InterweavedBinaryTree::append_bits64(uint64_t bits, uint32_t bit_len) {
  const uint32_t start = bitbuf_.total_bits;
  for (uint32_t i = 0; i < bit_len; ++i) {
    const uint32_t bit = (bits >> (bit_len - 1u - i)) & 1u;
    const uint32_t bit_pos_in_word = bitbuf_.total_bits % 64;
    if (bit_pos_in_word == 0) bitbuf_.words.emplace_back(0ull);
    if (bit) bitbuf_.words.back() |= (uint64_t(1) << (63u - bit_pos_in_word));
    bitbuf_.total_bits += 1;
  }
  return start;
}

uint32_t InterweavedBinaryTree::match_prefix64(uint64_t key, uint32_t key_len, uint32_t start, const Node& n) const {
  const uint32_t len = n.prefix.bit_len;
  if (len == 0) return 0;
  const uint32_t limit = std::min(len, key_len - start);
  for (uint32_t i = 0; i < limit; ++i) {
    const uint32_t pb = bitbuf_bit(bitbuf_, n.prefix.bit_offset + i);
    const uint32_t kb = key_bit64(key, key_len, start + i);
    if (pb != kb) return i;
  }
  return limit;
}

void InterweavedBinaryTree::insert_core64(uint64_t key, uint32_t key_len, uint32_t* out_row_opt) {
  if (nodes_.empty()) {
    Node root{};
    root.prefix.bit_len = static_cast<uint16_t>(key_len);
    root.prefix.bit_offset = append_bits64(key, key_len);
    root.terminal = 1;
    if (out_row_opt) root.terminal_row_id = *out_row_opt;
    nodes_.push_back(root);
    return;
  }
  uint32_t cur = 0;
  uint32_t consumed = 0;
  while (true) {
    Node& n = nodes_[cur];
    const uint32_t matched = match_prefix64(key, key_len, consumed, n);
    if (matched < n.prefix.bit_len) {
      const uint32_t common = matched;
      const uint32_t old_rem_len = n.prefix.bit_len - common;
      const uint64_t old_rem_bits = bitbuf_extract(bitbuf_, n.prefix.bit_offset + common, old_rem_len);
      const uint64_t common_bits = bitbuf_extract(bitbuf_, n.prefix.bit_offset, common);
      Node parent{};
      parent.prefix.bit_len = static_cast<uint16_t>(common);
      parent.prefix.bit_offset = append_bits64(common_bits, common);
      Node old_child = n;
      if (old_rem_len > 0) {
        const uint32_t old_branch = (old_rem_bits >> (old_rem_len - 1u)) & 1u;
        const uint32_t old_child_len = old_rem_len - 1u;
        const uint64_t old_child_bits = old_child_len == 0 ? 0 : (old_rem_bits & ((uint64_t(1) << old_child_len) - 1u));
        old_child.prefix.bit_len = static_cast<uint16_t>(old_child_len);
        old_child.prefix.bit_offset = append_bits64(old_child_bits, old_child_len);
        old_child.child0 = n.child0;
        old_child.child1 = n.child1;
        old_child.terminal = n.terminal;
        old_child.terminal_row_id = n.terminal_row_id;
        n = parent;
        const uint32_t old_idx = static_cast<uint32_t>(nodes_.size());
        nodes_.push_back(old_child);
        if (old_branch == 0) n.child0 = old_idx; else n.child1 = old_idx;
      } else {
        n = parent;
        n.terminal = old_child.terminal;
        n.terminal_row_id = old_child.terminal_row_id;
      }

      const uint32_t new_rem_len = key_len - consumed - common;
      if (new_rem_len == 0) {
        n.terminal = 1;
        if (out_row_opt) n.terminal_row_id = *out_row_opt;
        return;
      }
      const uint64_t new_rem_bits = key_slice64(key, key_len, consumed + common, new_rem_len);
      const uint32_t new_branch = (new_rem_bits >> (new_rem_len - 1u)) & 1u;
      const uint32_t new_child_len = new_rem_len - 1u;
      const uint64_t new_child_bits = new_child_len == 0 ? 0 : (new_rem_bits & ((uint64_t(1) << new_child_len) - 1u));
      Node new_leaf{};
      new_leaf.prefix.bit_len = static_cast<uint16_t>(new_child_len);
      new_leaf.prefix.bit_offset = append_bits64(new_child_bits, new_child_len);
      new_leaf.terminal = 1;
      if (out_row_opt) new_leaf.terminal_row_id = *out_row_opt;
      const uint32_t new_idx = static_cast<uint32_t>(nodes_.size());
      nodes_.push_back(new_leaf);
      if (new_branch == 0) n.child0 = new_idx; else n.child1 = new_idx;
      return;
    }
    consumed += matched;
    if (consumed == key_len) {
      n.terminal = 1;
      if (out_row_opt) n.terminal_row_id = *out_row_opt;
      return;
    }
    const uint32_t next_bit = key_bit64(key, key_len, consumed);
    uint32_t& child = (next_bit == 0 ? n.child0 : n.child1);
    if (child == 0xFFFFFFFFu) {
      const uint32_t rem_len = key_len - consumed - 1u;
      const uint64_t rem_bits = key_slice64(key, key_len, consumed + 1u, rem_len);
      Node leaf{};
      leaf.prefix.bit_len = static_cast<uint16_t>(rem_len);
      leaf.prefix.bit_offset = append_bits64(rem_bits, rem_len);
      leaf.terminal = 1;
      if (out_row_opt) leaf.terminal_row_id = *out_row_opt;
      child = static_cast<uint32_t>(nodes_.size());
      nodes_.push_back(leaf);
      return;
    }
    cur = child;
    consumed += 1u;
  }
}

bool InterweavedBinaryTree::contains_core64(uint64_t key, uint32_t key_len) const {
  if (nodes_.empty()) return false;
  uint32_t cur = 0;
  uint32_t consumed = 0;
  while (true) {
    const Node& n = nodes_[cur];
    const uint32_t matched = match_prefix64(key, key_len, consumed, n);
    if (matched < n.prefix.bit_len) return false;
    consumed += matched;
    if (consumed == key_len) return n.terminal != 0;
    const uint32_t next_bit = key_bit64(key, key_len, consumed);
    const uint32_t child = (next_bit == 0 ? n.child0 : n.child1);
    if (child == 0xFFFFFFFFu) return false;
    cur = child;
    consumed += 1u;
  }
}

void InterweavedBinaryTree::insert64(const Key64& k) {
  insert_core64(k.v, k.bits, nullptr);
}

bool InterweavedBinaryTree::contains64(const Key64& k) const {
  return contains_core64(k.v, k.bits);
}

void InterweavedBinaryTree::build_from_sorted64_impl(const std::vector<Key64>& keys,
                                                     uint32_t l, uint32_t r,
                                                     uint32_t bit_pos_from_msb,
                                                     uint32_t parent_idx) {
  if (l >= r) return;
  const uint32_t key_len = keys[l].bits;
  uint32_t common = 0;
  while (bit_pos_from_msb + common < key_len) {
    const uint32_t b0 = key_bit64(keys[l].v, key_len, bit_pos_from_msb + common);
    bool ok = true;
    for (uint32_t i = l + 1; i < r; ++i) {
      const uint32_t bi = key_bit64(keys[i].v, keys[i].bits, bit_pos_from_msb + common);
      if (bi != b0) { ok = false; break; }
    }
    if (!ok) break;
    ++common;
  }
  Node node{};
  node.prefix.bit_len = static_cast<uint16_t>(common);
  if (common > 0) {
    const uint64_t seg = key_slice64(keys[l].v, key_len, bit_pos_from_msb, common);
    node.prefix.bit_offset = append_bits64(seg, common);
  }
  const uint32_t cur_idx = static_cast<uint32_t>(nodes_.size());
  nodes_.push_back(node);
  if (parent_idx != 0xFFFFFFFFu) {
    Node& p = nodes_[parent_idx];
    const uint32_t next_bit = key_bit64(keys[l].v, key_len, bit_pos_from_msb);
    if (next_bit == 0) p.child0 = cur_idx; else p.child1 = cur_idx;
  }
  if (bit_pos_from_msb + common == key_len) {
    nodes_[cur_idx].terminal = 1;
    return;
  }
  const uint32_t next_pos = bit_pos_from_msb + common;
  uint32_t mid = l;
  while (mid < r) {
    const uint32_t b = key_bit64(keys[mid].v, keys[mid].bits, next_pos);
    if (b == 1) break;
    ++mid;
  }
  if (l < mid) build_from_sorted64_impl(keys, l, mid, next_pos + 1, cur_idx);
  if (mid < r) build_from_sorted64_impl(keys, mid, r, next_pos + 1, cur_idx);
}

void InterweavedBinaryTree::build_from_sorted_keys64(const std::vector<Key64>& sorted_keys) {
  nodes_.clear(); bitbuf_.words.clear(); bitbuf_.total_bits = 0;
  if (sorted_keys.empty()) return;
  build_from_sorted64_impl(sorted_keys, 0, static_cast<uint32_t>(sorted_keys.size()), 0, 0xFFFFFFFFu);
}

bool InterweavedBinaryTree::vector_prefix_exists64(uint64_t prefix, uint32_t prefix_len) const {
  if (nodes_.empty()) return false;
  std::stack<std::pair<uint32_t, uint32_t>> st;
  st.push({0u, 0u});
  while (!st.empty()) {
    const auto [cur, consumed] = st.top(); st.pop();
    const Node& n = nodes_[cur];
    const uint32_t matched = match_prefix64(prefix, prefix_len, consumed, n);
    if (matched < std::min<uint32_t>(n.prefix.bit_len, prefix_len - consumed)) continue;
    const uint32_t new_consumed = consumed + matched;
    if (new_consumed >= prefix_len) return true;
    const uint32_t next_bit = key_bit64(prefix, prefix_len, new_consumed);
    const uint32_t child = (next_bit == 0 ? n.child0 : n.child1);
    if (child != 0xFFFFFFFFu) st.push({child, new_consumed + 1u});
  }
  return false;
}

InterweavedBinaryTree InterweavedBinaryTree::matvec_or_cpu(const InterweavedBinaryTree& matrix,
                                                           const InterweavedBinaryTree& vector) {
  BitsSpec out_spec = make_spec_from_bits({matrix.spec_.bits_per_level[0]});
  InterweavedBinaryTree out(out_spec);
  if (matrix.spec_.levels != 2 || vector.spec_.levels != 1) return out;
  const uint32_t bits_row = matrix.spec_.bits_per_level[0];
  const uint32_t bits_col = matrix.spec_.bits_per_level[1];
  const uint32_t total_bits = matrix.spec_.total_bits;
  struct Slot { uint32_t dim; };
  std::vector<Slot> sched; sched.reserve(total_bits);
  uint32_t max_plane = std::max(bits_row, bits_col);
  for (uint32_t plane = 0; plane < max_plane; ++plane) {
    if (plane < bits_row) sched.push_back({0u});
    if (plane < bits_col) sched.push_back({1u});
  }
  std::vector<uint64_t> rows_set_bits; rows_set_bits.reserve(1024);
  struct Frame { uint32_t node; uint32_t consumed; uint64_t row_prefix; uint32_t row_len; uint64_t col_prefix; uint32_t col_len; };
  std::stack<Frame> st;
  st.push({0u, 0u, 0ull, 0u, 0ull, 0u});
  while (!st.empty()) {
    Frame f = st.top(); st.pop();
    const Node& n = matrix.nodes_[f.node];
    if (n.prefix.bit_len > 0) {
      const uint64_t seg = bitbuf_extract(matrix.bitbuf_, n.prefix.bit_offset, n.prefix.bit_len);
      for (uint32_t i = 0; i < n.prefix.bit_len; ++i) {
        const uint32_t bit = (seg >> (n.prefix.bit_len - 1u - i)) & 1u;
        const auto& slot = sched[f.consumed + i];
        if (slot.dim == 0) {
          f.row_prefix = (f.row_prefix << 1) | bit;
          ++f.row_len;
        } else {
          f.col_prefix = (f.col_prefix << 1) | bit;
          ++f.col_len;
        }
      }
      f.consumed += n.prefix.bit_len;
    }
    if (f.col_len > 0) {
      if (!vector.vector_prefix_exists64(f.col_prefix, f.col_len)) continue;
    }
    if (n.terminal && f.consumed == total_bits) {
      rows_set_bits.push_back(f.row_prefix);
    }
    if (n.child0 != 0xFFFFFFFFu) {
      Frame nf = f;
      const auto& slot = sched[nf.consumed];
      if (slot.dim == 0) { nf.row_prefix = (nf.row_prefix << 1); ++nf.row_len; }
      else { nf.col_prefix = (nf.col_prefix << 1); ++nf.col_len; }
      nf.consumed += 1u;
      nf.node = n.child0;
      st.push(nf);
    }
    if (n.child1 != 0xFFFFFFFFu) {
      Frame nf = f;
      const auto& slot = sched[nf.consumed];
      if (slot.dim == 0) { nf.row_prefix = (nf.row_prefix << 1) | 1u; ++nf.row_len; }
      else { nf.col_prefix = (nf.col_prefix << 1) | 1u; ++nf.col_len; }
      nf.consumed += 1u;
      nf.node = n.child1;
      st.push(nf);
    }
  }
  if (rows_set_bits.empty()) return out;
  std::sort(rows_set_bits.begin(), rows_set_bits.end());
  std::vector<Key64> vk;
  vk.reserve(rows_set_bits.size());
  for (auto r : rows_set_bits) vk.push_back(Key64{r, bits_row});
  out.build_from_sorted_keys64(vk);
  return out;
}

struct DeviceHandle {};

void InterweavedBinaryTree::upload_to_device(DeviceHandle*& dev) const { dev = ibt_cuda_upload(this); }
void InterweavedBinaryTree::release_device(DeviceHandle*& dev) { if (!dev) return; ibt_cuda_release(dev); dev = nullptr; }

InterweavedBinaryTree InterweavedBinaryTree::matvec_or_gpu(const InterweavedBinaryTree& matrix,
                                                           const InterweavedBinaryTree& vector) {
  BitsSpec out_spec = make_spec_from_bits({matrix.spec_.bits_per_level[0]});
  InterweavedBinaryTree out(out_spec);
  if (matrix.spec_.levels != 2 || vector.spec_.levels != 1) return out;
  DeviceHandle* dm = nullptr;
  DeviceHandle* dv = nullptr;
  matrix.upload_to_device(dm);
  vector.upload_to_device(dv);
  DeviceHandle* d_out = nullptr;
  ibt_cuda_matvec_or(dm, dv, d_out);
  std::vector<Key64> keys;
  ibt_cuda_download_keys(d_out, keys);
  out.build_from_sorted_keys64(keys);
  release_device(dm);
  release_device(dv);
  release_device(d_out);
  return out;
}

void InterweavedBinaryTree::insert128(const Key128& k) {
  if (k.bits <= 64) {
    insert_core64(k.hi, k.bits, nullptr);
    return;
  }
  assert(false && "128-bit insert requires 128-bit path; use 64-bit for <=64");
}

bool InterweavedBinaryTree::contains128(const Key128& k) const {
  if (k.bits <= 64) return contains_core64(k.hi, k.bits);
  return false;
}

void InterweavedBinaryTree::build_from_sorted_keys128(const std::vector<Key128>& sorted_keys) {
  if (sorted_keys.empty()) return;
  if (sorted_keys[0].bits <= 64) {
    std::vector<Key64> keys;
    keys.reserve(sorted_keys.size());
    for (const auto& k : sorted_keys) keys.push_back(Key64{k.hi, k.bits});
    build_from_sorted_keys64(keys);
    return;
  }
  assert(false && "128-bit build requires 128-bit path; use 64-bit for <=64");
}

bool InterweavedBinaryTree::vector_prefix_exists128(const Key128& prefix, uint32_t prefix_len) const {
  if (prefix_len <= 64) return vector_prefix_exists64(prefix.hi, prefix_len);
  return false;
}

} // namespace ibt
