package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;

/**
 * 推理单元算子（照搬伪代码 {@code inference_cell_op.py}）：N 步前向链，C/F 链式传递。
 * <p>
 * initialC 是跨轮传承点：首轮清零，非首轮读上一轮输出的 C。每步内 C = y.C、F = y.F 链式传递。
 * <p>
 * 方向标记用 /* -&gt; *&#47; 注释（设计原则第5条）：左边入参，右边出参。
 * <p>
 * 伪代码 {@code y.C}/{@code y.F}（零拷贝视图）的 Java 等价：{@code mapper.extractC(y, stream)} /
 * {@code mapper.extractF(y, stream)}——映射器内部工作缓冲，算子签名与伪代码一致（无 buf_c/buf_f 出参）。
 */
public final class InferenceCellOp {

    private InferenceCellOp() {
    }

    public static void inferenceCellOp(FittableMapper mapper, int N, boolean[][] gSeq, long dt,
                                       VectorBase initialC, VectorBase initialF, long stream /* -> */,
                                       VectorBase y, VectorBase bufX) {
        fw(mapper, N, gSeq, dt, initialC, initialF, stream /* -> */, bufX, y);
    }

    private static void fw(FittableMapper mapper, int N, boolean[][] gSeq, long dt,
                           VectorBase C, VectorBase F, long stream /* -> */,
                           VectorBase bufX, VectorBase y) {
        for (int i = 0; i < N; i++) {
            mapper.assembleX(C, F, gSeq[i], dt, stream /* -> */, bufX);
            mapper.fw(bufX, stream /* -> */, y, null);
            C = mapper.extractC(y, stream);
            F = mapper.extractF(y, stream);
        }
    }
}
