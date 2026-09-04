package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.core.execution.event.Event;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common.AncSlider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.InputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.OutputVectorDomain;

/**
 * 算法状态：锚点滑轨、遗传信息、传承 tC、工作草稿、流程边事件（照搬伪代码 {@code urana_state.py}）。
 * <p>
 * 算法过程（fast_tick/slow_tick）零持有——世界是过程的集合，状态归环境保管；
 * 两过程即 state'=tick(state,…) 的原地转移。不叫 Anchor：所持远不止锚点，
 * 且避免与领域概念锚点（Anc）撞名。
 * <p>
 * G（boolean[]）与 dt（long）的转换由 FloatVector.setRegion 重载在内部完成（设计原则第2条）。
 * y.C/y.F 的区域抽取由 mapper.extractC/extractF 的内部工作缓冲承担（无 buf_c/buf_f 字段）。
 * <p>
 * <b>依赖接口</b>（真善美第2条）：mapper 字段为 {@link FittableMapper} 接口类型——
 * 允许附属模组插入实现该接口的装饰器层（日志/量化/蒸馏）到 process→nn 之间，
 * UranaState/UranaSystem 无需感知具体 mapper 家族。
 */
public class UranaState {

    public final FittableMapper mapper;
    public final OutputVectorDomain outputDomain;
    public final int behaviorLen;
    public final int cLen;

    // 三环锚点滑轨（prospective 无 outputDomain：快环推 Susp 用完整输出）
    public final AncSlider prospectiveAncSlider;
    public final AncSlider retrospectiveAncSlider;
    public final AncSlider introspectiveAncSlider;

    // 遗传信息 ×3（首轮清零；非首轮由 inference 回填后自然延续）
    public final VectorBase prospectiveInheritance;
    public final VectorBase retrospectiveInheritance;
    public final VectorBase introspectiveInheritance;

    // 传承 tC ×3（首轮清零；tC 绝不跨环共享）
    public final VectorBase prospectiveTC;
    public final VectorBase retrospectiveTC;
    public final VectorBase introspectiveTC;

    // 工作草稿（快环）
    public final VectorBase fastY;
    public final VectorBase fastBufX;

    // 工作草稿（慢环，最大 N=2）
    public final VectorBase[] slowYs;
    public final Object[] slowFwTraces;
    public final VectorBase slowBufX;
    public final VectorBase buf_t;

    // 快→慢的流程边：快环 record，慢环 tick 前等待（天然解决首拍问题）
    // 三缓冲（照搬原初代理模式）：快环每轮 record traceEvent[traceGen%3]，traceGen++；
    // 慢环取 latestGen=traceGen-1，waitEvent(traceEvent[latestGen%3])。
    // 单 event 的致命缺陷：CUDA event 被 record 过后，后续 waitEvent 立即通过——
    // 慢环 while 循环 CPU 不阻塞（CUDA stream waitEvent 是 GPU 等待非 CPU 等待），
    // 疯狂提交 GradCellOp 命令，GPU 被 8fw+8bw 梯度计算独占，OpenGL 渲染被饿死。
    // 三缓冲让慢环每轮 waitEvent 等的是特定的 traceEvent[tIdx]——只有快环 record
    // 到该槽位时才通过，形成"慢环→等快环→快环等视觉→视觉等渲染"的链条，
    // GPU 在梯度计算与渲染之间交替，不独占。
    public final Event[] traceEvent;
    public volatile int traceGen = 0;

    public UranaState(FittableMapper mapper) {
        this.mapper = mapper;
        InputVectorDomain inputDomain = mapper.getInputDomain();
        OutputVectorDomain outputDomain = mapper.getOutputDomain();
        this.outputDomain = outputDomain;
        int feelingSize = inputDomain.getFeelingSpan().getLength();
        int behaviorSize = outputDomain.getBehaviorSpan().getLength();
        this.behaviorLen = behaviorSize;
        this.cLen = outputDomain.getInheritanceInfoSpan().getLength();

        // 三环锚点滑轨
        this.prospectiveAncSlider = new AncSlider(mapper, feelingSize, behaviorSize, null);
        this.retrospectiveAncSlider = new AncSlider(mapper, feelingSize, behaviorSize, outputDomain);
        this.introspectiveAncSlider = new AncSlider(mapper, feelingSize, behaviorSize, outputDomain);

        // 遗传信息 ×3（首轮清零）
        // 用 mapper.zeroVector 而非硬编码 FloatVector cast——兼容 BoolVector（BNN）与
        // FloatVector（CNN）两种载体（真善美第2/3条：上层 state 不感知下层 nn 载体家族）。
        this.prospectiveInheritance = mapper.createVector(this.cLen);
        this.retrospectiveInheritance = mapper.createVector(this.cLen);
        this.introspectiveInheritance = mapper.createVector(this.cLen);
        mapper.zeroVector(0L /* -> */, this.prospectiveInheritance);
        mapper.zeroVector(0L /* -> */, this.retrospectiveInheritance);
        mapper.zeroVector(0L /* -> */, this.introspectiveInheritance);

        // 传承 tC ×3（首轮清零）
        // tC 用前向载体（createVector）而非梯度载体（createGradientVector）——
        // 伪代码 grad_cell_op.py 中 tC 兼任 fw 的 C 输入（前向）与 bw 的 buf_tC 出参（梯度外拷），
        // CNN 下两者同体（FloatVector）无矛盾；BNN 下前向=BoolVector、梯度=IntVector 不兼容，
        // 故 tC 统一用前向载体，BNN backward 写 bufTc 时用 negateAndBinarize 把 IntVector 梯度
        // 转成 BoolVector 前向（与原初代理 gradientToInput 同款 bit 转换，藏于 nn 家族内核）。
        this.prospectiveTC = mapper.createVector(this.cLen);
        this.retrospectiveTC = mapper.createVector(this.cLen);
        this.introspectiveTC = mapper.createVector(this.cLen);
        mapper.zeroVector(0L /* -> */, this.prospectiveTC);
        mapper.zeroVector(0L /* -> */, this.retrospectiveTC);
        mapper.zeroVector(0L /* -> */, this.introspectiveTC);

        // 工作草稿（快环）
        this.fastY = mapper.createVector(outputDomain.totalLength());
        this.fastBufX = mapper.createVector(inputDomain.totalLength());

        // 工作草稿（慢环，最大 N=2）
        this.slowYs = new VectorBase[2];
        this.slowFwTraces = new Object[2];
        for (int i = 0; i < 2; i++) {
            this.slowYs[i] = mapper.createVector(outputDomain.totalLength());
            this.slowFwTraces[i] = mapper.createFwTraceForBw();
        }
        this.slowBufX = mapper.createVector(inputDomain.totalLength());
        this.buf_t = mapper.createVector(outputDomain.totalLength());

        // 快→慢的流程边：三缓冲（照搬原初代理模式）
        this.traceEvent = new Event[3];
        for (int i = 0; i < 3; i++) {
            this.traceEvent[i] = new Event();
        }
    }
}
