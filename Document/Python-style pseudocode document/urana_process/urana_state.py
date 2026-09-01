from core import Event
from urana_process.common.anc_slider import AncSlider


class UranaState:
    """算法状态：锚点滑轨、遗传信息、传承 tC、工作草稿、流程边事件。

    算法过程（fast_tick/slow_tick）零持有——世界是过程的集合，状态归环境保管；
    两过程即 state'=tick(state,…) 的原地转移。不叫 Anchor：所持远不止锚点，
    且避免与领域概念锚点（Anc）撞名。
    """

    def __init__(self, mapper):
        self.mapper = mapper
        inputDomain, outputDomain = mapper.inputDomain, mapper.outputDomain
        self.outputDomain = outputDomain
        feelingSize = inputDomain.getFeelingSpan().getLength()
        behaviorSize = outputDomain.getBehaviorSpan().getLength()
        self.behaviorLen = behaviorSize
        self.cLen = outputDomain.getInheritanceInfoSpan().getLength()

        # 三环锚点滑轨（prospective 无 outputDomain：快环推 Susp 用完整输出）
        self.prospectiveAncSlider = AncSlider(mapper, feelingSize, behaviorSize)
        self.retrospectiveAncSlider = AncSlider(mapper, feelingSize, behaviorSize, outputDomain)
        self.introspectiveAncSlider = AncSlider(mapper, feelingSize, behaviorSize, outputDomain)

        # 遗传信息 ×3 + 传承 tC ×3（首轮清零；tC 绝不跨环共享）
        self.prospectiveInheritance = mapper.createVector(self.cLen)
        self.retrospectiveInheritance = mapper.createVector(self.cLen)
        self.introspectiveInheritance = mapper.createVector(self.cLen)
        for inh in self.prospectiveInheritance, self.retrospectiveInheritance, self.introspectiveInheritance:
            inh.multiplyByScalar("<-", 0)  # 首轮无传承：清零，非首轮由 inference 回填后自然延续
        self.prospectiveTC = mapper.createGradientVector(self.cLen)
        self.retrospectiveTC = mapper.createGradientVector(self.cLen)
        self.introspectiveTC = mapper.createGradientVector(self.cLen)
        for tC in self.prospectiveTC, self.retrospectiveTC, self.introspectiveTC:
            mapper.zeroGradient(None, "->", tC)

        # 工作草稿按过程归属：快环/慢环各持一套，消除跨流竞态；草稿无身份，
        # 每次调用整体重写，同流内顺序复用（慢环最大 N=2）
        self.fastY = mapper.createVector(outputDomain.totalLength())
        self.fastBufX = mapper.createVector(inputDomain.totalLength())
        self.slowYs = [mapper.createVector(outputDomain.totalLength()) for _ in range(2)]
        self.slowFwTraces = [mapper.createFwTraceForBw() for _ in range(2)]
        self.slowBufX = mapper.createVector(inputDomain.totalLength())
        self.buf_t = mapper.createVector(outputDomain.totalLength())

        # 快→慢的流程边：快环 record，慢环 tick 前等待（天然解决首拍问题）
        self.pushEvent = Event()
