"""urana 的算法流程（纯函数版）：世界是过程的集合。

快环一拍（fast_tick）与慢环一轮（slow_tick）是仅有的两个过程；锚点滑轨、
遗传信息、传承 tC、工作草稿等"事物"由调用方（UranaSystem）持有并注入。
本文件不 import os / Event / AncSlider——算法文件只知道过程，不知道事物。
"""

from core import Span
from urana_process.semantics.containers.io.subspan.behavior_span import BehaviorSpan
from urana_process.common.grad_cell_op import grad_cell_op
from urana_process.common.inference_cell_op import inference_cell_op
from urana_process.common.urana_constants import G_PAST_1, G_FUTURE_1, G_FUTURE_N, G_PAST_N


def fast_tick(mapper, state, currentFeeling, dtMillis, visionEvent, fastStream, _: "->", behaviorChannel, buf_state):
    """快环一拍：从"现在"的实际感觉出发，往返推理出本拍行为并发布。

    state: 入参，被读（取 inheritance/span/outputDomain）。
    buf_state: 出参，被写（fastY 推理产出、prospectiveInheritance 回填、滑轨 push、pushEvent）。
    behaviorChannel: 外部出参，发布本拍行为。
    调用点以同一 state 对象注入两侧——原地转移，类比 bw 的 tC/buf_tC 双占。
    visionEvent: 宿主视觉事件，None 则不等。
    """
    if visionEvent is not None:
        fastStream.waitEvent("<-", visionEvent)

    # 前瞻推理（Java ProspectiveInference）：推理无 target，直接从"现在"（宿主写入的
    # 实际感觉，零滞后）出发往返两步——第一步向未来 n 刻（G_FUTURE_N），第二步反推回
    # 现在（G_PAST_N），得到现在这一刻的行为，输出与输入 F 同属一个时刻；与慢环训练
    # 语境 [Prec, Susp, Prec] 的往返模式互为推理/训练两侧；dt 只反映工作环境的计算
    # 速度，由慢环测量，快环不自测 dt
    inference_cell_op(mapper, 2, [G_FUTURE_N, G_PAST_N], dtMillis, state.prospectiveInheritance, currentFeeling, fastStream, "->", buf_state.fastY, buf_state.fastBufX)

    behaviorChannel.getBuffer().copyRegionFrom("<-", state.fastY, state.outputDomain.getBehaviorSpan(), BehaviorSpan(0, state.behaviorLen), fastStream)
    buf_state.prospectiveInheritance.copyRegionFrom("<-", state.fastY, state.outputDomain.getInheritanceInfoSpan(), Span(0, state.cLen), fastStream)

    behaviorChannel.publish("<-", fastStream)

    # 快环职责：更新悬浮物锚点——本拍"现在"的感觉 + 本拍推理出的行为，构成完整时刻；
    # record 通知慢环（慢环 tick 前等待，天然解决首拍问题）
    buf_state.prospectiveAncSlider.pushSuspensionFrom("<-", currentFeeling, state.fastY, fastStream, state.outputDomain)
    buf_state.pushEvent.record("<-", fastStream)


def slow_tick(mapper, state, dtMillis, uranaStream, _: "->", buf_state, buf_mapper):
    """慢环一轮：等快环 → 三环训练（前瞻梯度 → 回溯推理+梯度 → 内省推理 → 训练语境）。

    state: 入参，被读（取锚点/tC/span）。
    buf_state: 出参，被写（滑轨 tick/push、tC 原地、inheritance 回填、草稿复用）。
    buf_mapper: 出参，被写（grad 的 bw 更新 nn 权重）。调用点以同一 mapper 注入两位。
    调用点以同一 state/mapper 对象注入两侧——原地转移，类比 bw 的 tC/buf_tC 双占。
    """
    uranaStream.waitEvent("<-", state.pushEvent)
    # 慢环职责：滑动锚点滑动者——最近的悬浮时刻沉淀为 Prec
    buf_state.prospectiveAncSlider.tick("<-")

    # 前瞻梯度（Java ProspectiveGradCell）：推理方向朝未来，校准方向相反 → G_PAST_1，
    # 用真实数据向过去拟合；anc_seq=[Susp, Prec]；传承自 prospectiveTC
    grad_cell_op(mapper, 1, [G_PAST_1], dtMillis, [state.prospectiveAncSlider.getSuspensionAnc(), state.prospectiveAncSlider.getPrecipitateAnc()], state.prospectiveTC, uranaStream, "->", buf_state.slowYs, buf_state.slowFwTraces, buf_state.slowBufX, buf_state.buf_t, buf_state.prospectiveTC, buf_mapper)

    # 回溯推理（Java RetrospectiveInference）：从"现在"（Susp）向过去推理一刻 → G_PAST_1
    inference_cell_op(mapper, 1, [G_PAST_1], dtMillis, state.retrospectiveInheritance, state.retrospectiveAncSlider.getSuspensionAnc().F, uranaStream, "->", buf_state.slowYs[0], buf_state.slowBufX)
    buf_state.retrospectiveAncSlider.tick("<-")
    buf_state.retrospectiveAncSlider.pushSuspensionFromOutput("<-", state.slowYs[0], uranaStream)
    buf_state.retrospectiveInheritance.copyRegionFrom("<-", state.slowYs[0], state.outputDomain.getInheritanceInfoSpan(), Span(0, state.cLen), uranaStream)

    # 回溯梯度（Java RetrospectiveGradCell）：推理方向朝过去，校准方向相反
    # → G_FUTURE_1（拟合未来1刻）；anc_seq=[Prec, Susp]；传承自 retrospectiveTC
    grad_cell_op(mapper, 1, [G_FUTURE_1], dtMillis, [state.retrospectiveAncSlider.getPrecipitateAnc(), state.retrospectiveAncSlider.getSuspensionAnc()], state.retrospectiveTC, uranaStream, "->", buf_state.slowYs, buf_state.slowFwTraces, buf_state.slowBufX, buf_state.buf_t, buf_state.retrospectiveTC, buf_mapper)

    buf_state.introspectiveAncSlider.pushPrecipitateFrom("<-", state.retrospectiveAncSlider.getSuspensionAnc(), uranaStream)
    # 内省推理（Java IntrospectiveInference）：以沉淀的过去（Prec）为起点想象 → G_FUTURE_1
    inference_cell_op(mapper, 1, [G_FUTURE_1], dtMillis, state.introspectiveInheritance, state.introspectiveAncSlider.getPrecipitateAnc().F, uranaStream, "->", buf_state.slowYs[0], buf_state.slowBufX)
    buf_state.introspectiveAncSlider.pushSuspensionFromOutput("<-", state.slowYs[0], uranaStream)
    buf_state.introspectiveInheritance.copyRegionFrom("<-", state.slowYs[0], state.outputDomain.getInheritanceInfoSpan(), Span(0, state.cLen), uranaStream)

    # 训练语境（Java TrainingContext）：[Prec, Susp, Prec]——从现在的 Prec 想象未来（Susp），
    # 再反推现在，target 回到起点 Prec；起点与终点是同一个"现在"，训练的是想象的自洽性。
    # 梯度单元的 fw 每步取 anchor 存储的真实 F（贴地重放），仅 C 链式携带想象状态；
    # 前向输出与真实 F/B 的偏差构成训练信号，使映射有数学基础地朝实际靠近。
    # 传承自 introspectiveTC
    grad_cell_op(mapper, 2, [G_FUTURE_N, G_PAST_N], dtMillis, [state.introspectiveAncSlider.getPrecipitateAnc(), state.introspectiveAncSlider.getSuspensionAnc(), state.introspectiveAncSlider.getPrecipitateAnc()], state.introspectiveTC, uranaStream, "->", buf_state.slowYs, buf_state.slowFwTraces, buf_state.slowBufX, buf_state.buf_t, buf_state.introspectiveTC, buf_mapper)
