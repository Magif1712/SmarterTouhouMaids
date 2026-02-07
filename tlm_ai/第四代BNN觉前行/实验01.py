import time

current_value = 0

while True:
    binary_str = format(current_value, '09b')
    # print(f"原始二进制: {binary_str}")

    # 提取偶数位和奇数位
    even_bits = [binary_str[i] for i in range(len(binary_str)) if i % 2 == 0]  # 索引0,2,4,6,8
    odd_bits = [binary_str[i] for i in range(len(binary_str)) if i % 2 == 1]   # 索引1,3,5,7

    # 转换为字符串
    even_str = ''.join(even_bits)  # 偶数位二进制
    odd_str = ''.join(odd_bits)    # 奇数位二进制

    # 按偶数位 -> 奇数位顺序包装成数组
    result = [even_str, odd_str]

    # 输出结果
    # print(f"偶数位: {even_str}, 奇数位: {odd_str}")
    print(f"包装后的数组: {result}")

    # 判断是否达到全1（511）
    if current_value == 511:
        print("已达到全1，程序停止。")
        break

    current_value += 1  # 递增
