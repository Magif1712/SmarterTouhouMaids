from urana_process.fittable_mapper.nn.original_cnn.mapping.training.cnn_training_ops_native import ___cnnBackwardLayer

# 桥接层（对应 Java CnnTrainingOps）：接收上层对象，提取句柄，调用原生方法。
# bufHp 始终更新权重+刷新 cache（从 bufHp 提取 p/q/l/r/b/idx/w 句柄）。
# bufTc 非 None 时外拷输入层梯度（仅 C 部分，sizeC 限制写入范围）；None 时跳过（传 0）。
#
# 跨 NN 权重更新要求结构一致：读侧 hp 算梯度、写侧 bufHp 落权重，
# 结构不同会越界——故校验 sizeA0/sizeA1 一致。
# lr（学习率）由调用方（AbstractCnnNeuralNetwork）提供，作为训练配置不污染权重容器。


def cnnBackwardLayer(trace, hp, target, stream, _: "->", x, dz, dInput, bufTc, bufHp, lr):
    if bufHp.getSizeA0() != hp.getSizeA0() or bufHp.getSizeA1() != hp.getSizeA1():
        raise ValueError("buf_hp 与 hp 结构不一致，无法跨 NN 更新权重。")
    bufTcHandle = bufTc.requireHandle() if bufTc is not None else 0
    sizeC = bufTc.size() if bufTc is not None else 0
    ___cnnBackwardLayer(
        trace.z.requireHandle(), trace.y.requireHandle(),
        target.requireHandle(), x.requireHandle(),
        hp.getP().requireHandle(), hp.getQ().requireHandle(),
        hp.getL().requireHandle(), hp.getR().requireHandle(), hp.getB().requireHandle(),
        hp.getIdx0().requireHandle(), hp.getIdx1().requireHandle(),
        hp.getW0().requireHandle(), hp.getW1().requireHandle(),
        hp.getSizeA0(), hp.getSizeA1(), sizeC, lr, stream,
        bufHp.getP().requireHandle(), bufHp.getQ().requireHandle(),
        bufHp.getL().requireHandle(), bufHp.getR().requireHandle(), bufHp.getB().requireHandle(),
        bufHp.getIdx0().requireHandle(), bufHp.getIdx1().requireHandle(),
        bufHp.getW0().requireHandle(), bufHp.getW1().requireHandle(),
        dz.requireHandle(), dInput.requireHandle(), bufTcHandle)
