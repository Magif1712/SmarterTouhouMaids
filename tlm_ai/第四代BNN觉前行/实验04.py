import numpy as np
import numba as nb
import time




# 创建2^30个随机布尔值的数组（约10亿个元素）
# 注意：直接创建这么大的数组可能会占用约1GB内存
# 为了演示，我们创建一个较小的数组并测试
# 实际测试时可以取消注释下面的代码
n = 2 ** 30
# n = 2**20  # 1048576个元素，约1MB，便于测试
a = np.random.choice([False, True], size=n)

# 压缩布尔数组
start_time = time.time()
packed = np.packbits(a)
compression_time = time.time() - start_time
print(f"压缩时间: {compression_time:.4f}秒")
print(f"原始大小: {a.nbytes / 1024 / 1024:.2f} MB")
print(f"压缩后大小: {packed.nbytes / 1024 / 1024:.2f} MB")


# 定义获取指定位的函数
@nb.njit(fastmath=True)
def get_bit(packed, index):
    """使用Numba加速的位提取函数"""
    byte_idx = index >> 3  # 等价于 index // 8
    bit_idx = index & 7  # 等价于 index % 8
    return (packed[byte_idx] & (1 << bit_idx)) != 0
# def get_bit_from_packed(packed, index):
#     byte_index = index // 8
#     bit_offset = index % 8
#     mask = 1 << bit_offset
#     return (packed[byte_index] & mask) != 0


# 测试访问特定位置的性能
test_positions = [
    0,  # 第一个元素
    1,  # 第二个元素
    7,  # 第一个字节的最后一位
    8,  # 第二个字节的第一位
    359696867 % n,  # 您指定的位置（取模以适应测试数组大小）
    n // 2,  # 中间位置
    n - 1  # 最后一个元素
]

# 执行多次测试取平均值
num_trials = 1000
for pos in test_positions:
    start_time = time.time()
    for _ in range(num_trials):
        value = get_bit(packed, pos)
    avg_time = (time.time() - start_time) / num_trials
    print(f"位置 {pos}: 值 = {value}, 平均访问时间 = {avg_time * 1000:.6f} 毫秒")

# 与直接访问原始数组比较
for pos in test_positions:
    start_time = time.time()
    for _ in range(num_trials):
        value = a[pos]
    avg_time = (time.time() - start_time) / num_trials
    print(f"原始数组位置 {pos}: 值 = {value}, 平均访问时间 = {avg_time * 1000:.6f} 毫秒")
