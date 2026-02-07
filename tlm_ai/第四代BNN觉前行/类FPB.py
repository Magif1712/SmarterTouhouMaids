import numpy as np
import copy
import threading

from 类BNN import BNN
from collections import deque


class FPB:
    """
    第一代注释
    #     觉前行(The feeling of the future, the behavior of the past)通用人工智能系统
    #     因为创作者的习惯，称神经网络为三角形(triangle)，感觉信息为正方形(C)，行为信息为圆形(X)，
    # 另外还有一种信息，我称之为C，它负责把过去传来的梯度继承起来，对于任意一个单一时刻的这些信息的总和我称之为记忆元
    #     融合梯度的时候需要让y_goal_merge中的感觉信息与行为信息和y_true中的相同以保证实事求是、尊重事实，只有C信息会真正地传递下去
    #     "第一个"X元素为推理未来索引，"第二个"X元素为推理过去索引，它们都是二进制自然数
    #     为了兼顾精确回忆，原先只需要一个来回的combo活塞虫改为两个来回，顺序为"1向过去->2向未来->3向未来->4向过去"
    #     在一定程度上涉及模型的自蒸馏，即自己蒸馏自己，用自己对过去的认识蒸馏出对未来的预测
    最新代注释
        对应表
    传承	    Transmission    承 C
    记忆	    Memory          痕 H (从本质上看这也是所有感觉的一部分)
    实际感觉	Feel            觉 J
    实践	    Praxis          行 X (从本质上看这也是所有感觉的一部分)
    认识	    Cognition       识 S
        先是C，然后是H，然后是J，最后是X
        因为"承"是拿来的，"痕"是刻上的，所以
        对应表
    拿来 NL   承
    刻上 KS   痕
    """

    def __init__(self, C_shape, J_shape, X_shape, func_receptor_once, func_effector_once):
        # H是J和X的痕，所以H_shape由J_shape和X_shape决定
        H_shape = (J_shape[0] + X_shape[0], J_shape[1])
        if not H_shape[1] == J_shape[1] == X_shape[1] == C_shape[1]:  # 只要存在任意两个不同
            print(
                "H_shape[1]、J_shape[1]、X_shape[1]、C_shape[1]中存在不同,H_shape、J_shape、X_shape、C_shape形状不匹配"
            )
        self.layers_shape = [
            [C_shape[0] + H_shape[0] + J_shape[0] + X_shape[0], J_shape[1]],
            [C_shape[0] + H_shape[0] + J_shape[0] + X_shape[0], J_shape[1]],
            [C_shape[0] + H_shape[0] + J_shape[0] + X_shape[0], J_shape[1]],
            [C_shape[0] + H_shape[0] + J_shape[0] + X_shape[0], J_shape[1]]
        ]
        self.S = BNN(self.layers_shape)
        # 初始化C,H,J,X的shape
        self.C_shape = C_shape
        self.H_shape = H_shape
        self.J_shape = J_shape
        self.X_shape = X_shape
        # 初始化self.X_now
        self.X_now = np.zeros((self.X_shape[0], self.X_shape[1]))
        # 初始化感受器和效应器的运行函数
        self.func_receptor_once = func_receptor_once
        self.func_effector_once = func_effector_once
        # 初始化一个最大长度为2的瞬时记忆队列
        self.immediate_memory = deque(maxlen=2)
        # 初始化一个最大长度为2的时间向反方向流动的精神世界的瞬时记忆队列
        self.immediate_memory_opposite = deque(maxlen=2)
        # 初始运行
        # 1.初始化相应的C,H,X信息并收集第一个J信息然后第一次向瞬时记忆写入信息
        # 1.1.
        C = np.zeros((self.C_shape[0], self.C_shape[1]))
        J = self.func_receptor_once()
        X = np.zeros((self.X_shape[0], self.X_shape[1]))
        # 1.2.初始化实时H信息(这样就不用同步Cycles_L与NL_KS_Cycle而降低反应速度了)
        self.H = np.concatenate([J, X], axis=0)
        # 1.3.写入瞬时记忆
        self.immediate_memory.append(
            np.concatenate([self.H, J, X], axis=0)
        )
        # 2.合理地填满瞬时记忆队列
        for _ in range(2):
            C = self.Cycles_L_once(C)
        # 启动Cycles_L
        threading.Thread(target=self.Cycles_L).start()
        # 启动记忆器
        threading.Thread(target=self.NL_KS_Cycle).start()
        # 启动"combo活塞虫"训练(ComboPistonWorm，缩写为CPW)
        threading.Thread(target=self.CPW_Cycle).start()
        # 激活与启动效应器
        threading.Thread(target=self.func_effector_Cycle).start()

    def func_effector_Cycle(self):
        while True:
            self.func_effector_once(self.X_now)

    def Cycles_L_once(self, C):  # a_neg_1是过去的总输出
        # 准备C,H,J,X要素，C已经传入了
        H = copy.deepcopy(self.H)
        J = self.func_receptor_once()
        X_past = self.immediate_memory[-1][
                 self.H_shape[0] + self.J_shape[0]:self.H_shape[0] + self.J_shape[0] + self.X_shape[0], :]
        # "combo活塞虫"推理
        # 向未来计算输出，因为输入的X本质上算是一种感觉，向未来的话是"第一个"X元素为1，"第二个"X元素为0，所以要进行赋值
        # （事实上这是为了给可能的迭代做准备，如果索引的值拓展到整数域的话就可以由行为X控制现在的输入感觉X的向过去索引和向未来索引了）
        X_past[0, 0] = 1
        X_past[1, 0] = 0
        a_neg_1 = self.S.forward(
            np.concatenate([C, H, J, X_past], axis=0)
        )[0][-1]
        # 向过去计算输出，因为输入的X本质上算是一种感觉，向过去的话是"第一个"X元素为0，"第二个"X元素为1，所以要进行赋值
        a_neg_1[self.C_shape[0] + self.H_shape[0] + self.J_shape[0], 0] = 0
        a_neg_1[self.C_shape[0] + self.H_shape[0] + self.J_shape[0] + 1, 0] = 1
        a_neg_1 = self.S.forward(a_neg_1)[0][-1]
        # 提取出X，即为当前的行为
        self.X_now = copy.deepcopy(
            a_neg_1[
            self.C_shape[0] + self.H_shape[0] + self.J_shape[0]
            :self.C_shape[0] + self.H_shape[0] + self.J_shape[0] + self.X_shape[0],
            :]
        )
        # 最后再向未来计算输出
        a_neg_1[self.C_shape[0] + self.H_shape[0] + self.J_shape[0], 0] = 1
        a_neg_1[self.C_shape[0] + self.H_shape[0] + self.J_shape[0] + 1, 0] = 0
        a_neg_1 = self.S.forward(a_neg_1)[0][-1]
        # 提取更新后的C并传出，还有更新瞬时记忆
        C = a_neg_1[0:self.C_shape[0], :]
        self.immediate_memory.append(np.concatenate([H, J, self.X_now], axis=0))
        return C

    def Cycles_L(self):
        # 初始化C
        C = np.zeros((self.C_shape[0], self.C_shape[1]))
        # 循环运行
        while True:
            C = self.Cycles_L_once(C)

    def NL_KS_once(self, y_goal_merge, C):
        """NL,拿来（承）;KS,刻上（痕）"""
        # NL
        # 提取传承目标值
        C_goal = y_goal_merge[0:self.C_shape[0], :]
        # 向过去计算输出并训练，因为输入的X本质上算是一种感觉，向过去的话是"第一个"X元素为0，"第二个"X元素为1，所以要进行赋值
        immediate_memory_1_prev = np.concatenate([C, self.immediate_memory[1]], axis=0)
        immediate_memory_1_prev[self.C_shape[0] + self.H_shape[0] + self.J_shape[0], 0] = 0
        immediate_memory_1_prev[self.C_shape[0] + self.H_shape[0] + self.J_shape[0] + 1, 0] = 1
        # 然后要把y_goal_merge中的C_goal_merge取出并融合到目标值中
        y_true = np.concatenate([C_goal, self.immediate_memory[0]], axis=0)
        activations, y_goal_merge_out = self.S.backward_spreading_forward(immediate_memory_1_prev, y_true)
        C_out = activations[-1][0: self.C_shape[0], :]
        # KS
        self.H = activations[-1][
                 self.C_shape[0] + self.H_shape[0]:self.C_shape[0] + self.H_shape[0] + self.J_shape[0] + self.X_shape[0]
        , :]
        return y_goal_merge_out, C_out

    def NL_KS_Cycle(self):
        # 初始化y_goal_merge
        C_goal = np.zeros((self.C_shape[0], self.C_shape[1]))
        y_goal_merge = np.concatenate([C_goal, self.immediate_memory[0]], axis=0)
        C = np.zeros((self.C_shape[0], self.C_shape[1]))
        # 循环运行
        while True:
            y_goal_merge, C = self.NL_KS_once(y_goal_merge, C)

    def Cycles_R_once(self, a_neg_1):
        """用来为CPW提供素材"""
        # 向过去计算输出并训练，因为输入的X本质上算是一种感觉，向过去的话是"第一个"X元素为0，"第二个"X元素为1，所以要进行赋值
        a_neg_1[self.C_shape[0] + self.H_shape[0] + self.J_shape[0], 0] = 0
        a_neg_1[self.C_shape[0] + self.H_shape[0] + self.J_shape[0] + 1, 0] = 1
        # 然后要把y_goal_merge中的C_goal_merge取出并融合到目标值中
        a_neg_1 = self.S.forward(a_neg_1)[0][-1]
        C_R_out = a_neg_1[0: self.C_shape[0], :]
        # 为CPW提供素材
        self.immediate_memory_opposite.append(
            a_neg_1[self.C_shape[0]:self.C_shape[0] + self.H_shape[0] + self.J_shape[0] + self.X_shape[0], :]
        )
        return a_neg_1

    def CPW_once(self, C_goal, a_neg_1):
        # 初始化
        # 为CPW提供素材
        a_neg_1 = self.Cycles_R_once(a_neg_1)
        # 推理顺序是"1向未来->2向过去->3向未来"则训练顺序是"1向过去->2向未来->3向过去"
        # 得准备C,H,J,X要素，
        # 由于是反向传播，所以输入层的C没有之前输出的值可以确定其数值，根据该项目的原理，此时应给输入层的C赋在{0,1}内的随机值
        H_J_X_past = self.immediate_memory_opposite[1]
        H_J_X_Present = self.immediate_memory_opposite[0]
        # 向未来计算输出并训练，因为输入的X本质上算是一种感觉，向未来的话是"第一个"X元素为1，"第二个"X元素为0，所以要进行赋值
        C = np.random.randint(0, 2, size=(self.C_shape[0], self.C_shape[1]))
        H_J_X_past[self.H_shape[0] + self.J_shape[0], 0] = 1
        H_J_X_past[self.H_shape[0] + self.J_shape[0] + 1, 0] = 0
        # 因为目标值应求真实不该赋值，所以目标值直接用瞬时记忆里的，另外，在这里activations好像没啥用
        activations, y_goal_merge_out = self.S.backward_spreading_forward(
            np.concatenate([C, H_J_X_past], axis=0), np.concatenate([C_goal, self.immediate_memory_opposite[0]], axis=0)
        )
        # 提取更新后的C_goal
        C_goal = y_goal_merge_out[0:self.C_shape[0], :]
        # 向过去计算输出并训练，因为输入的X本质上算是一种感觉，向过去的话是"第一个"X元素为0，"第二个"X元素为1，所以要进行赋值
        C = np.random.randint(0, 2, size=(self.C_shape[0], self.C_shape[1]))
        H_J_X_Present[self.H_shape[0] + self.J_shape[0], 0] = 0
        H_J_X_Present[self.H_shape[0] + self.J_shape[0] + 1, 0] = 1
        # 因为目标值应求真实不该赋值，所以目标值直接用瞬时记忆里的
        activations, y_goal_merge_out = self.S.backward_spreading_forward(
            np.concatenate([C, H_J_X_Present], axis=0),
            np.concatenate([C_goal, self.immediate_memory_opposite[1]], axis=0)
        )
        # 提取更新后的C_goal
        C_goal = y_goal_merge_out[0:self.C_shape[0], :]
        # 最后再向未来计算输出并训练
        C = np.random.randint(0, 2, size=(self.C_shape[0], self.C_shape[1]))
        H_J_X_past[self.H_shape[0] + self.J_shape[0], 0] = 1
        H_J_X_past[self.H_shape[0] + self.J_shape[0] + 1, 0] = 0
        # 因为目标值应求真实不该赋值，所以目标值直接用瞬时记忆里的
        activations, y_goal_merge_out = self.S.backward_spreading_forward(
            np.concatenate([C, H_J_X_past], axis=0), np.concatenate([C_goal, self.immediate_memory_opposite[0]], axis=0)
        )
        # 提取更新后的C_goal并传出，还有更新瞬时记忆
        C_goal = y_goal_merge_out[0:self.C_shape[0], :]
        return C_goal, a_neg_1

    def CPW_Cycle(self):
        # 初始化a_neg_1
        C_R_0 = np.zeros((self.C_shape[0], self.C_shape[1]))
        a_neg_1 = np.concatenate([C_R_0, self.immediate_memory[1]], axis=0)
        # 初始化反向瞬时记忆队列
        # 合理地填满反向瞬时记忆队列
        for _ in range(2):
            a_neg_1 = self.Cycles_R_once(a_neg_1)
        # 初始化C_goal
        C_goal = np.zeros((self.C_shape[0], self.C_shape[1]))
        while True:
            C_goal, a_neg_1 = self.CPW_once(C_goal, a_neg_1)
