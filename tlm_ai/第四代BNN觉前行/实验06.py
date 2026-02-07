import numpy as np


def parity_to_binary_direct(arr):
    """使用np.packbits和bitorder参数转换"""
    parity = arr & 1
    # 使用bitorder='little'确保位从右到左打包
    packed = np.packbits(parity, bitorder='little')
    # 使用byteorder='big'确保字节从左到右读取
    return int.from_bytes(packed.tobytes(), byteorder='big')


def parity_to_binary_truncated(arr):
    """使用np.packbits转换并正确处理字节顺序"""
    parity = arr & 1
    packed = np.packbits(parity)

    # 使用大端序，并根据数组长度正确提取位
    num_bits = len(parity)
    num_bytes = (num_bits + 7) // 8  # 计算需要的字节数

    # 如果数组长度不是8的倍数，需要特殊处理
    if num_bits % 8 != 0:
        # 获取最后一个字节的有效位数
        last_byte_bits = num_bits % 8
        # 创建掩码以清除最后一个字节中不需要的高位
        mask = (1 << last_byte_bits) - 1
        packed[-1] &= mask

    # 使用大端序转换为整数
    result = int.from_bytes(packed[:num_bytes].tobytes(), byteorder='big')
    return result


def manual_parity_to_binary(arr):
    """手动构建二进制数，作为参考"""
    result = 0
    for num in arr:
        result = (result << 1) | (num & 1)
    return result


# 测试不同长度的数组
test_arrays = [
    np.array([3, 4, 5]),  # 长度3
    np.array([3, 4, 5, 6, 7]),  # 长度5
    np.array([3, 4, 5, 6, 7, 8, 9]),  # 长度7
    np.array([3, 4, 5, 6, 7, 8, 9, 10]),  # 长度8 (8的倍数)
    np.array([3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 15, 19, 18]),  # 长度8 (8的倍数)
]

# 运行测试
for i, arr in enumerate(test_arrays):
    print(f"\n测试数组 {i + 1}: {arr}")
    print(f"奇偶性: {arr & 1}")

    direct_result = parity_to_binary_direct(arr)
    truncated_result = parity_to_binary_truncated(arr)
    manual_result = manual_parity_to_binary(arr)

    print(f"直接转换结果: {direct_result:b} (十进制: {direct_result})")
    print(f"修正后结果:  {truncated_result:b} (十进制: {truncated_result})")
    print(f"手动构建结果: {manual_result:b} (十进制: {manual_result})")

    # 验证修正后结果与手动构建结果是否一致
    if truncated_result == manual_result:
        print("✅ 修正后结果正确")
    else:
        print("❌ 修正后结果不正确")

    # 验证直接转换结果是否与手动构建结果一致
    if direct_result == manual_result:
        print("✅ 直接转换结果正确")
    else:
        print("❌ 直接转换结果不正确")
