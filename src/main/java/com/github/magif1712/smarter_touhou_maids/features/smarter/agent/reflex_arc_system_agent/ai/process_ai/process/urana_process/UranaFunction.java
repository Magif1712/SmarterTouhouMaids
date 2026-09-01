package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.core.execution.MappedGenerationBuffer;
import com.github.magif1712.smarter_touhou_maids.core.execution.event.Event;
import com.github.magif1712.smarter_touhou_maids.core.execution.stream.Stream;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common.Anc;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common.GradCellOp;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common.InferenceCellOp;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common.UranaConstants;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.BehaviorSpan;

/**
 * urana 的算法流程（纯函数版，照搬伪代码 {@code urana_function.py}）：世界是过程的集合。
 * <p>
 * 快环一拍（{@link #fastTick}）与慢环一轮（{@link #slowTick}）是仅有的两个过程；锚点滑轨、
 * 遗传信息、传承 tC、工作草稿等"事物"由调用方（UranaSystem）持有并注入。
 * 本文件不 import os / Event / AncSlider——算法文件只知道过程，不知道事物。
 * <p>
 * state（入参，被读）与 bufState（出参，被写）是同一对象在调用点的两位注入——原地转移，
 * 类比 bw 的 tC/buf_tC 双占（设计原则第5条：DPS 分离入参/出参）。
 * <p>
 * 方向标记用 /* -&gt; *&#47; 注释（设计原则第5条）：左边入参，右边出参。
 * <p>
 * <b>依赖接口</b>（真善美第2条）：mapper 参数为 {@link FittableMapper} 接口类型——
 * 算法层只认接口契约（assembleX/fw/bw/extractC/extractF…），不感知具体 mapper 家族，
 * 允许装饰器层插入。
 */
public final class UranaFunction {

    private UranaFunction() {
    }

    /**
     * 快环一拍：从"现在"的实际感觉出发，往返推理出本拍行为并发布。
     */
    public static void fastTick(FittableMapper mapper, UranaState state, VectorBase currentFeeling, long dtMillis, Event visionEvent, Stream fastStream /* -> */, MappedGenerationBuffer behaviorChannel, UranaState bufState) {
        long stream = fastStream.getHandle();

        if (visionEvent != null) {
            fastStream.waitEvent(/* <- */ visionEvent);
        }

        // 前瞻推理：N=2，[G_FUTURE_N, G_PAST_N]——向未来 n 刻再反推回现在
        InferenceCellOp.inferenceCellOp(mapper, 2, new boolean[][]{UranaConstants.G_FUTURE_N, UranaConstants.G_PAST_N}, dtMillis, state.prospectiveInheritance, currentFeeling, stream /* -> */, bufState.fastY, bufState.fastBufX);

        // 行为 → 外周通道
        behaviorChannel.getBuffer().copyRegionFrom(/* <- */ state.fastY, state.outputDomain.getBehaviorSpan(), new BehaviorSpan(0, state.behaviorLen), stream);
        // 行动者工作记忆更新（快环独占）
        bufState.prospectiveInheritance.copyRegionFrom(/* <- */ state.fastY, state.outputDomain.getInheritanceInfoSpan(), new Span(0, state.cLen) {}, stream);

        behaviorChannel.publish(/* <- */ stream);

        // 快环职责：更新悬浮物锚点——本拍感觉 + 本拍推理行为，构成完整时刻；record 通知慢环
        bufState.prospectiveAncSlider.pushSuspensionFrom(/* <- */ currentFeeling, state.fastY, stream, state.outputDomain);
        bufState.pushEvent.record(/* <- */ stream);
    }

    /**
     * 慢环一轮：等快环 → 三环训练（前瞻梯度 → 回溯推理+梯度 → 内省推理 → 训练语境）。
     */
    public static void slowTick(FittableMapper mapper, UranaState state, long dtMillis, Stream uranaStream /* -> */, UranaState bufState, FittableMapper bufMapper) {
        long stream = uranaStream.getHandle();

        // 等快环 → 滑动锚点
        uranaStream.waitEvent(/* <- */ state.pushEvent);
        bufState.prospectiveAncSlider.tick(/* <- */);

        // 前瞻梯度（prospective）：推理朝未来，校准朝过去 G_PAST_1；anc_seq=[Susp, Prec]；传承 prospectiveTC
        GradCellOp.gradCellOp(mapper, 1, new boolean[][]{UranaConstants.G_PAST_1}, dtMillis, new Anc[]{state.prospectiveAncSlider.getSuspensionAnc(), state.prospectiveAncSlider.getPrecipitateAnc()}, state.prospectiveTC, stream /* -> */, bufState.slowYs, bufState.slowFwTraces, bufState.slowBufX, bufState.buf_t, bufState.prospectiveTC, bufMapper);

        // 回溯推理：从现在（Susp）向过去推理一刻 G_PAST_1
        InferenceCellOp.inferenceCellOp(mapper, 1, new boolean[][]{UranaConstants.G_PAST_1}, dtMillis, state.retrospectiveInheritance, state.retrospectiveAncSlider.getSuspensionAnc().F, stream /* -> */, bufState.slowYs[0], bufState.slowBufX);
        bufState.retrospectiveAncSlider.tick(/* <- */);
        bufState.retrospectiveAncSlider.pushSuspensionFromOutput(/* <- */ state.slowYs[0], stream);
        bufState.retrospectiveInheritance.copyRegionFrom(/* <- */ state.slowYs[0], state.outputDomain.getInheritanceInfoSpan(), new Span(0, state.cLen) {}, stream);

        // 回溯梯度：推理朝过去，校准朝未来 G_FUTURE_1；anc_seq=[Prec, Susp]；传承 retrospectiveTC
        GradCellOp.gradCellOp(mapper, 1, new boolean[][]{UranaConstants.G_FUTURE_1}, dtMillis, new Anc[]{state.retrospectiveAncSlider.getPrecipitateAnc(), state.retrospectiveAncSlider.getSuspensionAnc()}, state.retrospectiveTC, stream /* -> */, bufState.slowYs, bufState.slowFwTraces, bufState.slowBufX, bufState.buf_t, bufState.retrospectiveTC, bufMapper);

        // 内省推理：以沉淀的过去（Prec）为起点想象未来 G_FUTURE_1
        bufState.introspectiveAncSlider.pushPrecipitateFrom(/* <- */ state.retrospectiveAncSlider.getSuspensionAnc(), stream);
        InferenceCellOp.inferenceCellOp(mapper, 1, new boolean[][]{UranaConstants.G_FUTURE_1}, dtMillis, state.introspectiveInheritance, state.introspectiveAncSlider.getPrecipitateAnc().F, stream /* -> */, bufState.slowYs[0], bufState.slowBufX);
        bufState.introspectiveAncSlider.pushSuspensionFromOutput(/* <- */ state.slowYs[0], stream);
        bufState.introspectiveInheritance.copyRegionFrom(/* <- */ state.slowYs[0], state.outputDomain.getInheritanceInfoSpan(), new Span(0, state.cLen) {}, stream);

        // 训练语境：[Prec, Susp, Prec]——从现在的 Prec 想象未来再反推现在；传承 introspectiveTC
        GradCellOp.gradCellOp(mapper, 2, new boolean[][]{UranaConstants.G_FUTURE_N, UranaConstants.G_PAST_N}, dtMillis, new Anc[]{state.introspectiveAncSlider.getPrecipitateAnc(), state.introspectiveAncSlider.getSuspensionAnc(), state.introspectiveAncSlider.getPrecipitateAnc()}, state.introspectiveTC, stream /* -> */, bufState.slowYs, bufState.slowFwTraces, bufState.slowBufX, bufState.buf_t, bufState.introspectiveTC, bufMapper);
    }
}
