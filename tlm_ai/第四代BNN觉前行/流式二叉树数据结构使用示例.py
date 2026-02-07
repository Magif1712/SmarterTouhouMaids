import numpy as np
import sys
import numba
from 流式二叉树数据结构 import IBTree

"""
创建大型数组
"""
# 设置随机种子以便结果可重现
np.random.seed(42)

# 向量大小为2^28
vector_size = 2 ** 28

# 生成随机布尔数组
bool_array = np.random.randint(0, 2, vector_size, dtype=bool)

# 将布尔数组压缩为二进制数组（每8位压缩为1个字节）
packed_array = np.packbits(bool_array)

# 验证数组大小
print(f"原始布尔数组大小: {len(bool_array)}")
print(f"压缩后数组大小: {len(packed_array)}")
print(f"内存使用: {packed_array.nbytes / (1024 ** 2):.2f} MB")
"""
正式示例
"""
# 初始化
a_0 = packed_array
w = IBTree(56)
# 试验向量化且运算
w_out = None
# 检测a_0是否匹配w
if len(a_0) == 2 ** (w.num_levels / 2):
    w
