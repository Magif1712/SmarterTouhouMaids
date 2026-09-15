package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;

/**
 * 梯度单元算子（照搬伪代码 {@code grad_cell_op.py}）：两阶段（探索-修正）链式训练。
 * <p>
 * <b>C 通道四槽</b>（照搬伪代码；设计原则6 的原地读写双占写法，调用点两槽传同一对象）：
 * <ul>
 *     <li>{@code tC}/{@code tCSelf}——<b>C2 槽</b>：阶段一 bw 外拷落点，阶段二 fw 的起始 C。轮内草稿，不跨轮、不落盘。</li>
 *     <li>{@code buftC}/{@code buftCSelf}——<b>传承槽</b>：两阶段 bw 的 {@code assembleT} 目标源，轮末写回。跨轮持存。</li>
 * </ul>
 * 于是每轮的训练任务<b>非恒等</b>：阶段一种子取零、阶段二种子取 C2，而两阶段的<b>目标都取旧传承</b>。
 * <p>
 * 阶段一 fw 的 C 实参在伪代码里是字面 {@code 0}；Java 的 {@code VectorBase} 装不下一个标量，
 * 故实在化为「<b>先把 C2 槽清零，再作为阶段一的种子读</b>」（设计原则5：用实在的东西把不实在的东西转化为实在的东西），
 * 与 {@code ys[i].C} → {@code mapper.extractC} 属同类适配。
 * C2 槽是<b>轮内草稿</b>（不跨轮、不落盘、每轮整体重写），兼作阶段一起点不越界；
 * 且它与传承槽 {@code buftC} 是两个不同对象 ⇒ <b>清零它不会触及传承</b>
 * （旧结构下 {@code tC} 与传承同一对象，清零即毁传承——那条反对理由在新结构下已不成立）。
 * 由公理(3)：相比另设一个只读零向量 {@code zeroC} 作入参，本写法少一个参数、少一个环境字段、
 * 少一次原生分配 ⇒ 描述长度更短、熵更低；代价仅为每轮 1 次 {@code zeroVector}（cLen 长度，约 1% 慢环开销）。
 * 按设计原则6：清零是写入 ⇒ 用出参名 {@code tCSelf}；读取是入参 ⇒ 用原名 {@code tC}。
 * <p>
 * 方向标记用 /* -&gt; *&#47; 注释（设计原则第5条）：左边入参，右边出参。
 * <p>
 * 伪代码 {@code ys[i].C} 的 Java 等价：{@code mapper.extractC(ys[i], stream)}——算子签名与伪代码一致。
 * <p>
 * 重构：bw 传入 {@code ys[i]}——StoreTrace 下 trace_y 即 ys[i]，一份写入两方消费
 * （设计原则第5条：DPS 式编程，出参由调用方注入）。
 */
public final class GradCellOp {

    private GradCellOp() {
    }

    public static void gradCellOp(FittableMapper mapper, int N, boolean[][] gSeq, long dt,
                                  Anc[] ancSeq, VectorBase tC, VectorBase buftC, long stream /* -> */,
                                  VectorBase[] ys, Object[] fwTraces,
                                  VectorBase bufX, VectorBase bufT, VectorBase tCSelf,
                                  FittableMapper mapperSelf, VectorBase buftCSelf) {
        // 阶段一：从【零】探索＋反向。把 C2 槽清零当零种子（设计原则6：写入用 Self 名，读取用原名）；
        // 目标取传承 buftC，出参仍落 C2 槽 tCSelf——故清零不会触及传承（两者是不同对象）。
        mapper.zeroVector(stream /* -> */, tCSelf);
        fw(mapper, N, gSeq, dt, tC, ancSeq, stream /* -> */, bufX, ys, fwTraces);
        bw(mapper, N, fwTraces, ys, ancSeq, buftC, stream /* -> */, bufT, tCSelf, mapperSelf);

        // 阶段二：从 C2（写后的 tC）修正＋反向只更新权重；目标仍取旧传承 buftC（非 C2）⇒ 任务非恒等。
        fw(mapper, N, gSeq, dt, tC, ancSeq, stream /* -> */, bufX, ys, fwTraces);
        bw(mapper, N, fwTraces, ys, ancSeq, buftC, stream /* -> */, bufT, null, mapperSelf);

        // 轮末：传承 ← C2（写回）。buftCSelf 与 buftC 同一对象（双占），本用法下不可为 null。
        buftCSelf.copyRegionFrom(/* <- */ tC, new Span(0, tC.size()) {}, new Span(0, buftCSelf.size()) {}, stream);
    }

    private static void fw(FittableMapper mapper, int N, boolean[][] gSeq, long dt,
                           VectorBase tC, Anc[] ancSeq, long stream /* -> */,
                           VectorBase bufX, VectorBase[] ys, Object[] fwTraces) {
        // mapper 只读（不更新权重）⇒ 不设 mapperSelf（单槽，加 Self 是熵增）。
        // tC 只读：末尾 tC = extractC(...) 是局部重绑定，不构成对入参对象的写入 ⇒ 留在入参侧。
        for (int i = 0; i < N; i++) {
            mapper.assembleX(tC, ancSeq[i].F, gSeq[i], dt, stream /* -> */, bufX);
            mapper.fw(bufX, stream /* -> */, ys[i], fwTraces[i]);
            tC = mapper.extractC(ys[i], stream);
        }
    }

    private static void bw(FittableMapper mapper, int N, Object[] fwTraces, VectorBase[] ys, Anc[] ancSeq,
                           VectorBase tC, long stream /* -> */,
                           VectorBase bufT, VectorBase tCGrad, FittableMapper mapperSelf) {
        // mapper（入参）只读：assembleT 装配目标、bw 经其 nn 运算；mapperSelf（出参）非 null 时才更新权重。
        // tCGrad（出参/写）：非 null 外拷输入层梯度，null 不外拷。
        //   它与入参 tC 是两个不同对象（实参分别来自传承槽与 C2 槽）⇒ 不得写作 tCSelf
        //   （设计原则6：禁止在两个参数位传入除 null 之外的两个不同变量）。
        for (int i = N - 1; i >= 0; i--) {
            mapper.assembleT(tC, ancSeq[i + 1].F, ancSeq[i + 1].B, stream /* -> */, bufT);
            mapper.bw(tCGrad, mapperSelf /* <- */, fwTraces[i], ys[i], bufT, stream);
        }
    }
}
