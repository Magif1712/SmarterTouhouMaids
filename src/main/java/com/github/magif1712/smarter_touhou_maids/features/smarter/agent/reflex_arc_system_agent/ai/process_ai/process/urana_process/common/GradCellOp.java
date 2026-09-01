package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;

/**
 * 梯度单元算子（照搬伪代码 {@code grad_cell_op.py}）：两阶段（探索-修正）链式训练。
 * <p>
 * 阶段一从 tC 出发探索，bw 以 buf_tC 为出参（S1=C2 落位）；阶段二从 C2（写后的 tC）重放，
 * bw 只更新权重（buf_tC=null 跳过梯度外拷）。调用点以同一 tC/mapper 注入入参+出参两位（别名）。
 * <p>
 * 方向标记用 /* -&gt; *&#47; 注释（设计原则第5条）：左边入参，右边出参。
 * <p>
 * 伪代码 {@code ys[i].C} 的 Java 等价：{@code mapper.extractC(ys[i], stream)}——算子签名与伪代码一致。
 */
public final class GradCellOp {

    private GradCellOp() {
    }

    public static void gradCellOp(FittableMapper mapper, int N, boolean[][] G_seq, long dt,
                                  Anc[] anc_seq, VectorBase tC, long stream /* -> */,
                                  VectorBase[] ys, Object[] fw_traces,
                                  VectorBase buf_x, VectorBase buf_t, VectorBase buf_tC,
                                  FittableMapper buf_mapper) {
        // 阶段一：探索 + 反向（buf_tC 出参 → tC 原地更新为 C2）
        fw(mapper, N, G_seq, dt, tC, anc_seq, stream /* -> */, buf_x, ys, fw_traces);
        bw(mapper, N, fw_traces, anc_seq, tC, stream /* -> */, buf_t, buf_tC, buf_mapper);

        // 阶段二：从 C2（写后的 tC）重放 + 反向只更新权重（buf_tC=null 跳过梯度外拷）
        fw(mapper, N, G_seq, dt, tC, anc_seq, stream /* -> */, buf_x, ys, fw_traces);
        bw(mapper, N, fw_traces, anc_seq, tC, stream /* -> */, buf_t, null, buf_mapper);
    }

    private static void fw(FittableMapper mapper, int N, boolean[][] G_seq, long dt,
                           VectorBase C, Anc[] anc_seq, long stream /* -> */,
                           VectorBase buf_x, VectorBase[] ys, Object[] fw_traces) {
        for (int i = 0; i < N; i++) {
            mapper.assembleX(C, anc_seq[i].F, G_seq[i], dt, stream /* -> */, buf_x);
            mapper.fw(buf_x, stream /* -> */, ys[i], fw_traces[i]);
            C = mapper.extractC(ys[i], stream);
        }
    }

    private static void bw(FittableMapper mapper, int N, Object[] fw_traces, Anc[] anc_seq,
                           VectorBase tC, long stream /* -> */,
                           VectorBase buf_t, VectorBase buf_tC, FittableMapper buf_mapper) {
        for (int i = N - 1; i >= 0; i--) {
            mapper.assembleT(tC, anc_seq[i + 1].F, anc_seq[i + 1].B, stream /* -> */, buf_t);
            mapper.bw(fw_traces[i], buf_t, stream /* -> */, buf_tC, buf_mapper);
        }
    }
}
