from urana_process.fittable_mapper.i_fittable_mapper import FittableMapper


def inference_cell_op(mapper, N, G_seq, dt, initialC, initialF, stream, _: "->", y, buf_x):
    # initialC 是跨轮传承点：首轮清零，非首轮读上一轮输出的 C（由环境在 inheritance
    # 向量保管）。不依赖 buf_x 残留——每轮显式注入，步内再链式（C = y.C）。
    fw(mapper, N, G_seq, dt, initialC, initialF, stream, "->", buf_x, y)


def fw(mapper, N, G_seq, dt, C, F, stream, _: "->", buf_x, y):
    for i in range(N):
        mapper.assembleX(C, F, G_seq[i], dt, stream, "->", buf_x)
        mapper.fw(buf_x, stream, "->", y, None)
        C = y.C
        F = y.F
