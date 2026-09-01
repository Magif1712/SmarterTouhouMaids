from urana_process.fittable_mapper.i_fittable_mapper import FittableMapper


def grad_cell_op(mapper, N, G_seq, dt, anc_seq, tC, stream, _: "->", ys, fw_traces, buf_x, buf_t, buf_tC, buf_mapper):
    # 两阶段（探索-修正），两阶段都更新权重（防止 C 归零后无法自举脱困）。
    # tC（入参/读）：跨轮传承状态，本轮探索起点。buf_tC（出参/写）：C2 落点，回传给调用方传承。
    # buf_mapper（出参/写）：非 None 时 bw 更新权重；None 不更新。
    # 调用点以同一 tC/mapper 对象注入入参+出参两位（别名），原地更新——与 bw 的 tC/buf_tC 同构。
    # 阶段一从 tC 出发探索；其 bw 以 buf_tC 为出参——S1=C2 落位，
    # 且循环内 assembleT 先读 tC、bw 后写 buf_tC（同对象），天然实现链式传递（终端注入旧传承，
    # 非终端步注入上一步的输入层梯度）。阶段二从 C2（写后的 tC）重放，锚定在自己的出发点；
    # 其 bw 只更新权重（buf_tC=None 跳过梯度外拷）——S2 不传承，
    # 下一轮传承必须是 C2（被拟合过的输入点），不是 S2（从未作为前向起点的方向）。
    fw(mapper, N, G_seq, dt, tC, anc_seq, stream, "->", buf_x, ys, fw_traces)
    bw(mapper, N, fw_traces, anc_seq, tC, stream, "->", buf_t, buf_tC, buf_mapper)

    fw(mapper, N, G_seq, dt, tC, anc_seq, stream, "->", buf_x, ys, fw_traces)
    bw(mapper, N, fw_traces, anc_seq, tC, stream, "->", buf_t, None, buf_mapper)


def fw(mapper, N, G_seq, dt, C, anc_seq, stream, _: "->", buf_x, ys, fw_traces):
    for i in range(N):
        mapper.assembleX(C, anc_seq[i].F, G_seq[i], dt, stream, "->", buf_x)
        mapper.fw(buf_x, stream, "->", ys[i], fw_traces[i])
        C = ys[i].C


def bw(mapper, N, fw_traces, anc_seq, tC, stream, _: "->", buf_t, buf_tC, buf_mapper):
    # mapper（入参）只读：assembleT 装配目标、bw 经其 nn 运算——不改自身权重。
    # buf_mapper（出参/写）：非 None 更新权重（mapper 即 buf_mapper 则改到自身）；None 不更新。
    # buf_tC（出参/写）：非 None 外拷输入层梯度。
    for i in range(N - 1, -1, -1):
        mapper.assembleT(tC, anc_seq[i + 1].F, anc_seq[i + 1].B, stream, "->", buf_t)
        mapper.bw(fw_traces[i], buf_t, stream, "->", buf_tC, buf_mapper)
