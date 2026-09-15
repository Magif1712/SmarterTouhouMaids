from core import Span
from urana_process.fittable_mapper.i_fittable_mapper import FittableMapper


def grad_cell_op(mapper, N, G_seq, dt, anc_seq, tC, stream, buftC, _: "->",
                 ys, fw_traces, buf_x, buf_t, tCSelf, mapperSelf, buftCSelf):
    # mapper（入参/读）：四次 fw/bw 都经其 nn 运算；mapperSelf（出参/写）：非 None 更新权重、None 不更新。
    # tC（入参/读）：第二次 fw 的起始 C；tCSelf（出参/写）：承接第一次 bw 外拷的输入层梯度。
    #   二者同属一个原地读写对象，调用点两槽传同一对象（或其一为 None）。
    # buftC（入参/读）：两次 bw 的梯度种子；buftCSelf（出参/写）：末次 C 的外拷，同 buftC 一个对象（或 None）。
    fw(mapper, N, G_seq, dt, 0, anc_seq, stream, "->", buf_x, ys, fw_traces)
    bw(mapper, N, fw_traces, ys, anc_seq, buftC, stream, "->", buf_t, tCSelf, mapperSelf)
    fw(mapper, N, G_seq, dt, tC, anc_seq, stream, "->", buf_x, ys, fw_traces)
    bw(mapper, N, fw_traces, ys, anc_seq, buftC, stream, "->", buf_t, None, mapperSelf)

    # 轮末：传承 ← C2。原 `buftC = tC` 只是局部重绑定，调用方看不到；按实际 API 换成内容拷贝
    buftCSelf.copyRegionFrom("<-", tC, Span(0, tC.size()), Span(0, buftCSelf.size()), stream)


def fw(mapper, N, G_seq, dt, tC, anc_seq, stream, _: "->", buf_x, ys, fw_traces):
    # mapper 只读（不更新权重）⇒ 不设 mapperSelf（单槽，加 Self 是熵增）。
    # tC 只读：末尾 `tC = ys[i].C` 是局部重绑定，不构成对入参对象的写入 ⇒ 留在入参侧。
    for i in range(N):
        mapper.assembleX(tC, anc_seq[i].F, G_seq[i], dt, stream, "->", buf_x)
        mapper.fw(buf_x, stream, "->", ys[i], fw_traces[i])
        tC = ys[i].C


def bw(mapper, N, fw_traces, ys, anc_seq, tC, stream, _: "->", buf_t, tCGrad, mapperSelf):
    # mapper（入参/读）只读：assembleT 装配目标、bw 经其 nn 运算——不改自身权重。
    # mapperSelf（出参/写）：非 None 更新权重（mapper 即 mapperSelf 则改到自身）；None 不更新。
    # tCGrad（出参/写）：非 None 外拷输入层梯度；None 不外拷。
    #   它与入参 tC 是两个不同对象 ⇒ 不得写作 tCSelf（"禁止在两个参数位传入除了 null 之外的两个不同变量"）。
    for i in range(N - 1, -1, -1):
        mapper.assembleT(tC, anc_seq[i + 1].F, anc_seq[i + 1].B, stream, "->", buf_t)
        mapper.bw(tCGrad, mapperSelf, "<-", fw_traces[i], ys[i], buf_t, stream)
