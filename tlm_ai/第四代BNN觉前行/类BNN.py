import numpy as np


class BNN:
    """
    这个神经网络每一层神经元都是二维矩阵
    layers是形如"[[1,2],[3,2],[5,9],[4,6]]"的神经网络形状列表，其中"[1,2]"表示第一层神经元的矩阵形状，后面的同理
    """

    def __init__(self, layers_shape):
        assert len(layers_shape) == 4, "必须为4层网络"
        self.layers_shape = layers_shape  # 各层的矩阵形状
        self.weights = []  # 三维权重张量列表
        self.biases = []  # 二维偏置矩阵列表

        # 初始化权重和偏置（三维->四维权重）
        for i in range(3):  # 3组权重（连接4层）
            in_rows, in_cols = layers_shape[i]
            out_rows, out_cols = layers_shape[i + 1]
            # 权重形状：(out_rows, out_cols, in_rows, in_cols)
            # 生成 0 和 1 的随机整数（均匀分布，左闭右开区间 [low, high)）
            W = np.random.randint(low=0, high=2, size=(out_rows, out_cols, in_rows, in_cols), dtype=bool)
            b = np.zeros((out_rows, out_cols), dtype=bool)
            self.weights.append(W)
            self.biases.append(b)

    def f(self, x):
        """二维f函数"""
        return np.where(x <= 1, x, 1)

    def df(self, x):
        """f函数导数"""
        return np.where(x <= 1, 1, 0)

    # 仅提示
    def sg(self, x, b):
        """二维激活函数"""
        return abs(self.f(x) - b)

    # 仅提示
    def dsg(self, x, b):
        """激活函数导数"""
        return (self.f(x) - b) * self.df(x)

    def forward(self, x):
        """前向传播（输入应为二维矩阵组）"""
        activations = [x.astype(bool)]  # 存储各层激活矩阵
        pre_activations = []  # 存储加权和矩阵

        for i in range(3):  # 3次变换
            W = self.weights[i]
            b = self.biases[i]

            a_in = activations[-1]
            # 扩展激活值维度以匹配权重维度
            expanded_activations = a_in[np.newaxis, np.newaxis, :, :]
            # 按位与操作
            bitwise_and_result = expanded_activations & W
            # 沿着输入矩阵的行和列方向进行逻辑或操作
            z = np.any(bitwise_and_result, axis=(2, 3)).astype(bool)
            a_out = (self.f(z) ^ b).astype(bool)
            pre_activations.append(z)
            activations.append(a_out)
        return activations, pre_activations

    def backward_spreading_forward(self, x, y_true):
        """
        融合过去传来的梯度产生的新目标趋势，融合式反向传播，融合是在BNN外面进行的，
        y_true就是融合的结果，BNN内部只需要将首层的梯度也计算出来然后处理后传出即可
        """
        activations, pre_activations = self.forward(x)

        # 单样本反向传播
        delta_a = (activations[-1] - y_true)  # 注意：此处不除以 batch_size

        for layer in reversed(range(3)):
            df = self.df(pre_activations[layer])

            # 计算单样本梯度
            grad_b = -delta_a * (self.f(pre_activations[layer]) - self.biases[layer])

            a_prev = activations[layer]
            grad_W = np.einsum(
                'ij,kl->klij', a_prev,
                delta_a * (self.f(pre_activations[layer]) - self.biases[layer]) * df
            )

            # 更新 delta_a 用于下一层，由于要为梯度融合做准备，所以输入层的delta_a也要算
            delta_a = np.einsum('kl,klij->ij', delta_a * (self.f(pre_activations[layer]) - self.biases[layer]) * df,
                                self.weights[layer])

            # 参数更新
            self.weights[layer] = np.where(self.weights[layer] - grad_W <= 0.5, False, True)
            if layer != 2:  # 输出层的b必须恒为0
                self.biases[layer] = np.where(self.biases[layer] - grad_b <= 0.5, False, True)
        # 计算传出目标趋势并return，顺便将推理时的运算结果return出来
        y_goal_merge_out = np.where(x - delta_a <= 0.5, 0, 1)
        return activations, y_goal_merge_out
