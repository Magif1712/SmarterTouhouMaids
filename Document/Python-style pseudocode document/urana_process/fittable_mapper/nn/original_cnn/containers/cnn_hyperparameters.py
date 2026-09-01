import time
from core import FloatVector, IntVector


class CnnHyperparameters:
    """CNN 超参（单层）：把"CNN 权重 = 位置 p + 幅度 q + 左/右/偏置 l/r/b + 派生稀疏缓存 idx/w"
    这个不实在约束，实在化为一个对象（真善美第4条）。

    CNN 单层 n→m 映射（与 BNN 单层对称，但权重全为 32-bit float）：
      - p_i（位置，sizeA0）：目标输出位置，连续浮点，范围 [0, sizeA1)。前向时
        投影到最近两个输出位 j_0=⌊p⌋ 与 j_1=j_0+1，权重 w_k=1-(p-j_k)²。
      - q_i（幅度，sizeA0）：输入分量的缩放系数，范围 [0, 1)。
      - l_j / r_j / b_j（左/右/偏置，sizeA1）：邻域耦合与偏置项。
      - idx0/idx1（派生索引，IntVector sizeA0）：j_0/j_1 的有效索引（越界记 -1）。
      - w0/w1（派生权重，FloatVector sizeA0）：对应 idx 的插值权重。

    idx/w 是 p 的派生缓存（前向加速用），不是独立权重——
    save 只存语义权重 p/q/l/r/b，loadFromFile 后由调用方调
    CnnInferenceOps.cnnRefreshCache 重算 idx/w（非热路径，stream 0 + 同步）。

    新建时用 PCG 哈希随机填充（fillRandom）打破零吸引子（与 BNN 同理：
    全零权重→零输出→零梯度→永不更新）；五个权重向量用不同子种子（baseSeed + 0..4）避免
    同尺寸向量得到相同随机模式。仅此「新建」构造路径随机化；loadFromFile 走私有构造，
    保留磁盘预训练权重不覆盖。
    """

    def __init__(self, sizeA0, sizeA1, _p=None, _q=None, _l=None, _r=None, _b=None):
        self.sizeA0 = sizeA0
        self.sizeA1 = sizeA1
        if _p is not None:
            # loadFromFile 私有构造：保留磁盘预训练权重
            self.p = _p
            self.q = _q
            self.l = _l
            self.r = _r
            self.b = _b
        else:
            # 新建：PCG 随机初始化 p/q/l/r/b 打破零吸引子
            self.p = FloatVector(sizeA0)
            self.q = FloatVector(sizeA0)
            self.l = FloatVector(sizeA1)
            self.r = FloatVector(sizeA1)
            self.b = FloatVector(sizeA1)
            baseSeed = int(time.time_ns())
            # p 是目标位置，范围 [0, sizeA1)；随机化后输入分量均匀散射到输出空间各处。
            self.p.fillRandom("<-", float(sizeA1), baseSeed + 0)
            self.q.fillRandom("<-", 1.0, baseSeed + 1)
            self.l.fillRandom("<-", 1.0, baseSeed + 2)
            self.r.fillRandom("<-", 1.0, baseSeed + 3)
            self.b.fillRandom("<-", 1.0, baseSeed + 4)
        # idx/w 未初始化（垃圾值），由调用方调 cnnRefreshCache 填充
        self.idx0 = IntVector(sizeA0)
        self.idx1 = IntVector(sizeA0)
        self.w0 = FloatVector(sizeA0)
        self.w1 = FloatVector(sizeA0)

    def getSizeA0(self):
        return self.sizeA0

    def getSizeA1(self):
        return self.sizeA1

    def getP(self):
        return self.p

    def getQ(self):
        return self.q

    def getL(self):
        return self.l

    def getR(self):
        return self.r

    def getB(self):
        return self.b

    def getIdx0(self):
        return self.idx0

    def getIdx1(self):
        return self.idx1

    def getW0(self):
        return self.w0

    def getW1(self):
        return self.w1

    def save(self, folderPath):
        # 仅存语义权重 p/q/l/r/b；idx/w 不存（派生缓存，loadFromFile 后重算）
        self.p.save(folderPath + "/p.bin")
        self.q.save(folderPath + "/q.bin")
        self.l.save(folderPath + "/l.bin")
        self.r.save(folderPath + "/r.bin")
        self.b.save(folderPath + "/b.bin")

    @staticmethod
    def loadFromFile(folderPath):
        p = FloatVector.loadFromFile(folderPath + "/p.bin")
        q = FloatVector.loadFromFile(folderPath + "/q.bin")
        l = FloatVector.loadFromFile(folderPath + "/l.bin")
        r = FloatVector.loadFromFile(folderPath + "/r.bin")
        b = FloatVector.loadFromFile(folderPath + "/b.bin")
        sizeA0 = p.size()
        sizeA1 = b.size()
        return CnnHyperparameters(sizeA0, sizeA1, _p=p, _q=q, _l=l, _r=r, _b=b)

    def close(self): ...
