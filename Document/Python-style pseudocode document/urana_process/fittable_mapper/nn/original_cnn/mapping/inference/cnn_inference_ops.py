from urana_process.fittable_mapper.nn.original_cnn.mapping.inference.cnn_inference_ops_native import ___cnnForwardLayer, ___cnnRefreshCache

# 桥接层（对应 Java CnnInferenceOps）：接收上层对象，提取句柄，调用原生方法。
# fw_trace_for_bw 为 None 时纯推理（NoTrace：z 用 y 做工作区，push→pull→activate 覆盖 y）；
# 非 None 时训练前向（StoreTrace：z=trace.z 累加，y=trace.y 写 σ(z)）。
# cnnRefreshCache 由 p 重算 idx0/idx1/w0/w1（非热路径，stream 0 + 同步）。


def cnnForwardLayer(x, hp, stream, _: "->", y, fw_trace_for_bw):
    trace_z = fw_trace_for_bw.z.requireHandle() if fw_trace_for_bw is not None else 0
    trace_y = fw_trace_for_bw.y.requireHandle() if fw_trace_for_bw is not None else 0
    ___cnnForwardLayer(
        x.requireHandle(),
        hp.getP().requireHandle(), hp.getQ().requireHandle(),
        hp.getL().requireHandle(), hp.getR().requireHandle(), hp.getB().requireHandle(),
        hp.getIdx0().requireHandle(), hp.getIdx1().requireHandle(),
        hp.getW0().requireHandle(), hp.getW1().requireHandle(),
        hp.getSizeA0(), hp.getSizeA1(), stream,
        y.requireHandle(), trace_z, trace_y)


def cnnRefreshCache(hp, stream, _: "->"):
    ___cnnRefreshCache(
        hp.getP().requireHandle(), hp.getSizeA0(), hp.getSizeA1(), stream,
        hp.getIdx0().requireHandle(), hp.getIdx1().requireHandle(),
        hp.getW0().requireHandle(), hp.getW1().requireHandle())
