# 原生方法层（对应 Java CnnTrainingOpsNative）：声明 CUDA kernel 入口。
# 仅接收句柄与标量，不接触 Python 对象。
#
# 单一 _cnnBackwardLayer 方法承载两阶段反向（同一 host 函数内 launch 两个 kernel）：
#   Kernel1（sizeA1）：δ_j=2(y-target)·y(1-y)→dz[j]；∂l/∂r/∂b=δ·x[边界]；buf_l/r/b -= lr·grad
#   Kernel2（sizeA0）：∂x_i=Σδ[idx_k[i]]·q·w_k + δ[i+1]·l[i+1] + δ[i-1]·r[i-1]→dInput+bufTc；
#       ∂p/∂q from hp's p/q/idx/w；buf_p/q -= lr·grad；clamp buf_p[0,sizeA1-1]；refresh buf_idx/w
#
# bufHp 始终更新权重+刷新 cache。
# bufTc 为 0 时跳过外拷输入梯度（sizeC=0）。
# hp=读侧（前向权重），bufHp=写侧。l/r 输入梯度项读 hp（旧值，若 bufHp==hp 则已被 Kernel1
# 更新→可接受噪声，与 BNN 同理）。


def _cnnBackwardLayer(traceZ, traceY, target, x,
                      hp_p, hp_q, hp_l, hp_r, hp_b,
                      hp_idx0, hp_idx1, hp_w0, hp_w1,
                      sizeA0, sizeA1, sizeC, lr, stream,
                      buf_p, buf_q, buf_l, buf_r, buf_b,
                      buf_idx0, buf_idx1, buf_w0, buf_w1,
                      dz, dInput, bufTc): ...