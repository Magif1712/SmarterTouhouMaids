# 原生方法层（对应 Java CnnInferenceOpsNative）：声明 CUDA kernel 入口。
# 仅接收句柄与标量，不接触 Python 对象。
# traceZ/traceY 为 0 时走 NoTrace 路径（z 用 y 做工作区：push→pull→activate 覆盖 y）；
# 非 0 时走 StoreTrace 路径（z=traceZ 累加，y=traceY 写 σ(z)）。
# C 侧模板 <bool StoreTrace> 编译期优化。
#
# _cnnRefreshCache 由 p 重算 idx/w（非热路径，构造/loadFromFile 后一次性调用）。


def ___cnnForwardLayer(x, p, q, l, r, b, idx0, idx1, w0, w1, sizeA0, sizeA1, stream, y, traceZ, traceY): ...


def ___cnnRefreshCache(p, sizeA0, sizeA1, stream, idx0, idx1, w0, w1): ...
