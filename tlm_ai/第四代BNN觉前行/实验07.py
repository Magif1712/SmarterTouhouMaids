import numpy as np


def binary_to_decimal(binary_vector):
    """将二进制向量转换为十进制整数"""
    if binary_vector.ndim != 1:
        raise ValueError("输入必须是一维数组")
    if not np.all(np.logical_or(binary_vector == 0, binary_vector == 1)):
        raise ValueError("数组元素必须全部为0或1")

    # 计算权重
    n = len(binary_vector)
    weights = 2 ** np.arange(n - 1, -1, -1)

    # 加权求和
    decimal = np.sum(binary_vector * weights)
    return decimal


arr = np.array([3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 15, 19, 18])
a = arr & 1
decimal = binary_to_decimal(a)
print(a)
print(decimal)
b = np.packbits(a, bitorder='little')
print(b)
e = b.tobytes()
print(e)
c = int.from_bytes(e, byteorder='little')
print(c)
d = bin(c)
print(d)
