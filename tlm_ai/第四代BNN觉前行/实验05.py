import numpy as np
from bitarray import bitarray
import time


def generate_test_data(num_elements=int(64)):
    """生成测试数据"""
    return np.random.randint(0, 2 ** 28, size=num_elements)


def vector_to_binary(arr):
    parity = arr & 1
    packed = np.packbits(parity)
    result = int.from_bytes(packed.tobytes(), byteorder='little')
    # print(result)
    return result


def verify_result(arr, result):
    """验证结果正确性（对前1000个元素）"""
    print("验证结果正确性...")
    expected = 0
    # for i, num in enumerate(arr[:1000]):
    #     expected = (expected << 1) | (num & 1)

    # if expected == (result >> (len(arr) - 1000)) & ((1 << 1000) - 1):
    #     print("✅ 前1000个元素结果验证通过")
    # else:
    #     print("❌ 结果验证失败，请检查实现")


# 性能测试
def performance_test():
    # 生成测试数据（可根据内存调整大小）
    print("生成测试数据...")
    data = generate_test_data(num_elements=int(28))  # ?亿个元素
    print(f"数据大小: {data.nbytes / (1024 ** 3):.9f} GB")

    # 记录开始时间
    start_time = time.time()
    # 执行向量化方案
    result = extract_parity_vectorized(data)
    # 计算耗时
    elapsed = time.time() - start_time
    print(f"处理完成，耗时: {elapsed:.9f}秒")
    print(f"结果位数: {result.bit_length()}")

    # 验证结果
    verify_result(data, result)


# 执行测试
if __name__ == "__main__":
    performance_test()
