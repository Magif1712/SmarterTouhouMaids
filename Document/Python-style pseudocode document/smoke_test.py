# 流程层冒烟测试：用假对象验证 cell_op 的编排逻辑（调用序列、参数传递）
import sys
sys.path.insert(0, ".")


class FakeVec:
    def __init__(self, tag):
        self.tag = tag

    def size(self):
        return 1

    def copyRegionFrom(self, _, src, *args):
        calls.append(("copy", self.tag, src.tag))
        return None


class FakeAnc:
    def __init__(self, tag):
        self.F = FakeVec(tag + ".F")
        self.B = FakeVec(tag + ".B")


class FakeY:
    def __init__(self, tag):
        self.C = FakeVec(tag + ".C")
        self.F = FakeVec(tag + ".F")


class FakeTrace:
    pass


calls = []


def tag(v):
    return v.tag if hasattr(v, "tag") else v


class FakeMapper:
    def assembleX(self, C, F, G, dt, stream, _, buf_x):
        calls.append(("assembleX", tag(C), tag(F), G, dt))
        return None

    def assembleT(self, C, F, B, stream, _, buf_t):
        calls.append(("assembleT", tag(C), tag(F), tag(B)))
        return None

    def fw(self, x, stream, _, y, trace):
        calls.append(("fw", trace is not None))
        return None

    def bw(self, trace, t, stream, _, buf_tC, buf_mapper):
        calls.append(("bw", buf_tC, buf_mapper))
        return None


from urana_process.common.grad_cell_op import grad_cell_op
from urana_process.common.inference_cell_op import inference_cell_op

# ---- grad_cell_op: N=1, anc_seq=[a0, a1], 传承 tC ----
a0, a1 = FakeAnc("a0"), FakeAnc("a1")
ys = [FakeY("y0")]
traces = [FakeTrace()]
m = FakeMapper()
grad_cell_op(m, 1, ["G"], 16.0, [a0, a1], "tC", "stream", "->",
             ys, traces, "buf_x", "buf_t", "tC", m)

seq = [c[0] for c in calls]
assert seq == ["assembleX", "fw", "assembleT", "bw", "assembleX", "fw", "assembleT", "bw"], seq
assert calls[0] == ("assembleX", "tC", "a0.F", "G", 16.0)          # 阶段一从传承 tC 出发
assert calls[1] == ("fw", True)                                     # 第一阶段带 trace
assert calls[2] == ("assembleT", "tC", "a1.F", "a1.B")              # 阶段一 bw 以 tC 为锚（别名出参→原地+链式）
assert calls[3] == ("bw", "tC", m)                                     # 阶段一：更新权重 + C2 外拷落 tC
assert calls[4] == ("assembleX", "tC", "a0.F", "G", 16.0)          # 阶段二从 C2（原地后的 tC）出发
assert calls[5] == ("fw", True)
assert calls[6] == ("assembleT", "tC", "a1.F", "a1.B")              # 阶段二锚定自己的出发点 C2
assert calls[7] == ("bw", None, m)                                    # 阶段二：只更新权重，不外拷（S2 已废弃）
print("grad_cell_op OK: fw(1)->bw(1)->fw(1)->bw(1), 传承 tC 原地更新, 阶段二无外拷")

# ---- inference_cell_op: N=2, 链式 C/F 传递, 显式 initialC=0 ----
calls.clear()
inference_cell_op(FakeMapper(), 2, ["G0", "G1"], 5.0, 0, "f0", "stream", "->", FakeY("y"), "buf_x")
seq = [c[0] for c in calls]
assert seq == ["assembleX", "fw", "assembleX", "fw"], seq
assert calls[0][1] == 0 and calls[2][1] == "y.C"                    # 初始 C 显式为 0，之后从 y.C 链式
assert calls[0][2] == "f0" and calls[2][2] == "y.F"                 # F 从 y.F 链式传递
assert calls[1] == ("fw", False) and calls[3] == ("fw", False)      # 纯推理无 trace
print("inference_cell_op OK: N=2 链式推理, 初始 C 显式为 0, 无 trace")

# ---- 装配链: 注册表 → UranaProcessFactory → UranaSystem ----
from urana_process.fittable_mapper.nn.nn_registry import NnRegistry
from urana_process.fittable_mapper.fittable_mapper_registry import FittableMapperRegistry
from urana_process.fittable_mapper.nn.original_cnn.original_cnn_factory import CnnNnFactory
from urana_process.fittable_mapper.original_mapper.original_mapper_factory import OriginalMapperFactory
from urana_process.urana_process_factory import UranaProcessFactory

NnRegistry.register("cnn", CnnNnFactory())
FittableMapperRegistry.register("original_mapper", OriginalMapperFactory())

system = UranaProcessFactory().create(
    {"nn": "cnn", "mapper": "original_mapper", "fastMinDtMillis": 16, "slowMinDtMillis": 100}, None)
assert type(system).__name__ == "UranaSystem"
assert type(system.mapper).__name__ == "OriginalMapper"
assert type(system.mapper.nn).__name__ == "CnnNeuralNetwork"
assert system.fastMinDtMillis == 16 and system.slowMinDtMillis == 100
assert system.inputDomain.totalLength() == 24883217      # C(3F)+F+G(16)+dt(1)
assert system.outputDomain.totalLength() == 24883456     # C(3F)+F+B(256)
print("UranaProcessFactory OK: 注册表解析 + 选型先于构造 + 全链装配")

print("\nALL SMOKE TESTS PASSED")
