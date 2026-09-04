class CnnFwTraceForBw:
    """CNN 前向 trace（供反向使用）：承载单层的 z（pre-activation）与 y（activation）。

    与 BNN 的 fz 前向存储模式对称：前向 kernel 写 z+y，反向 kernel 读 y。

    反向时 σ'(z) = y(1-y)，故 y 是反向的充分信息
    （δ_j = 2(y_j - y'_j) · y_j(1-y_j)）。
    z 存储备用（数值检查/调试），反向 kernel 不直接读——σ'(z) 用 y 即可，
    避免重复计算 σ(z) 的浮点误差。

    资源容器：由 AbstractCnnNeuralNetwork.createFwTraceForBw 创建，
    close 时释放 z/y。
    """

    def __init__(self, z, y):
        # z: pre-activation 累加结果（push atomicAdd + pull_lr + b，未过 σ）
        # y: activation = σ(z)，前向最终输出，反向充分信息
        self.z = z
        self.y = y

    def getZ(self):
        return self.z

    def getY(self):
        return self.y

    def close(self): ...