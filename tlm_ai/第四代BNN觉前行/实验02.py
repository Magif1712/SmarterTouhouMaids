# import numpy as np
# import time
# import random
# import numba as nb
#
#
# # a = format(17, 'b')
# # print(a)
#
# # a = 0
# # if a:
# #     print(1)
# # else:
# #     print(0)
#
#
# # a = np.array([False] * 2048)
# # print(a)
#
#
# def generate_m_digit_random(m):
#     # 生成一个m位的随机整数
#     # 最高位不能为0，所以单独处理
#     first_digit = random.randint(1, 9)
#     remaining_digits = ''.join([str(random.randint(0, 9)) for _ in range(m - 1)])
#     return int(str(first_digit) + remaining_digits)
#
#
# # 生成并打印一个m位的随机整数
# # random_number = generate_m_digit_random(322122548)
#
# from bitarray import bitarray
#
# # 创建一个 bitarray 对象
# ba = bitarray('1010')  # 二进制表示为 [1, 0, 1, 0]
#
# # 验证单个位的布尔转换
# print(bool(ba[0]))  # 输出: True (对应值为 1)
# print(bool(ba[1]))  # 输出: False (对应值为 0)
#
# # 验证条件判断
# if ba[0]:
#     print("位置 0 是 1")  # 会被执行
#
# if not ba[1]:
#     print("位置 1 是 0")  # 会被执行
#
# # 与代码中的条件判断一致
# if not ba[1]:
#     print("条件判断通过")  # 会被执行
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


# 示例用法
if __name__ == "__main__":
    test_vectors = [
        # np.array([1, 0, 1, 0]),  # 10
        # np.array([1, 1, 1, 1]),  # 15
        # np.array([0, 0, 0, 1]),  # 1
        # np.array([1, 0, 0, 0, 0, 0, 0, 0])  # 128
        np.array([1, 0, 0, 0, 0, 0, 0, 0])  # 128
    ]

    for vec in test_vectors:
        decimal = binary_to_decimal(vec)
        print(f"二进制向量 {vec} 转换为十进制整数: {decimal}")
