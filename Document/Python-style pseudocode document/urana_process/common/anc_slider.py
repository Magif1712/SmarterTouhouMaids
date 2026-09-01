from urana_process.common.anc import Anc
from core import Span


class AncSlider:
    PRECIPITATE = 0
    SUSPENSION = 1

    def __init__(self, mapper, feelingSize, behaviorSize, outputDomain=None):
        def mk():
            return Anc(mapper.createVector(feelingSize), mapper.createVector(behaviorSize))
        self._pair = [mk(), mk()]
        self.outputDomain = outputDomain

    def getPrecipitateAnc(self):
        return self._pair[self.PRECIPITATE]

    def getSuspensionAnc(self):
        return self._pair[self.SUSPENSION]

    def tick(self, _: "<-"):
        self._pair.reverse()

    def pushSuspensionFrom(self, _: "<-", F, output, stream, outputDomain):
        susp = self.getSuspensionAnc()
        susp.F.copyRegionFrom("<-", F, Span(0, F.size()), Span(0, susp.F.size()), stream)
        susp.B.copyRegionFrom("<-", output, outputDomain.getBehaviorSpan(), Span(0, susp.B.size()), stream)

    def pushSuspensionFromOutput(self, _: "<-", source, stream):
        susp = self.getSuspensionAnc()
        susp.F.copyRegionFrom(
            "<-", source, self.outputDomain.getFeelingSpan(), Span(0, susp.F.size()), stream)
        susp.B.copyRegionFrom(
            "<-", source, self.outputDomain.getBehaviorSpan(), Span(0, susp.B.size()), stream)

    def pushPrecipitateFrom(self, _: "<-", anc, stream):
        prec = self.getPrecipitateAnc()
        prec.F.copyRegionFrom(
            "<-", anc.F, Span(0, anc.F.size()), Span(0, prec.F.size()), stream)
        prec.B.copyRegionFrom(
            "<-", anc.B, Span(0, anc.B.size()), Span(0, prec.B.size()), stream)

    def save(self, uranaPath, sliderId): ...

    def load(self, _: "<-", uranaPath, sliderId): ...

    def close(self): ...
