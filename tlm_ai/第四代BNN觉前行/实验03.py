import timeit
import random


def generate_m_digit_random(m):
    # 生成一个m位的随机整数
    # 最高位不能为0，所以单独处理
    first_digit = random.randint(1, 9)
    remaining_digits = ''.join([str(random.randint(0, 9)) for _ in range(m - 1)])
    return int(str(first_digit) + remaining_digits)


# 生成并打印一个m位的随机整数
random_number = generate_m_digit_random(20000)

n = random_number

bit_time = timeit.timeit(lambda: n & 1, number=1000000)
shift_time = timeit.timeit(lambda: (n >> 1) * 2 != n, number=1000000)
# str_time = timeit.timeit(lambda: int(bin(n)[2:][-1]), number=1000000)

print(f"位运算耗时: {bit_time:.6f}秒")
print(f"移位运算耗时: {shift_time:.6f}秒")
# print(f"字符串转换耗时: {str_time:.6f}秒")