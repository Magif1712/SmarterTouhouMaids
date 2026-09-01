from core import Span
from urana_process.fittable_mapper.i_fittable_mapper import FittableMapper


def _fullSpan(vector):
    return Span(0, vector.size())


class OriginalMapper(FittableMapper):
    def __init__(self, nn, inputDomain, outputDomain):
        self.nn = nn
        self.inputDomain = inputDomain
        self.outputDomain = outputDomain

    def assembleX(self, C, F, G, dt, stream, _: "->", buf_x):
        cSpan = self.inputDomain.getInheritanceInfoSpan()
        fSpan = self.inputDomain.getFeelingSpan()
        gSpan = self.inputDomain.getTargetTimeOrientationSpan()
        dtSpan = self.inputDomain.getFeelingBehaviorSamplingDtSpan()
        if C is not None:
            buf_x.setRegion("<-", cSpan, C, stream)
        if F is not None:
            buf_x.setRegion("<-", fSpan, F, stream)
        if G is not None:
            buf_x.setRegion("<-", gSpan, G, stream)
        if dt is not None:
            buf_x.setRegion("<-", dtSpan, dt, stream)

    def assembleT(self, C, F, B, stream, _: "->", buf_t):
        cSpan = self.outputDomain.getInheritanceInfoSpan()
        fSpan = self.outputDomain.getFeelingSpan()
        bSpan = self.outputDomain.getBehaviorSpan()
        if C is not None:
            buf_t.setRegion("<-", cSpan, C, stream)
        if F is not None:
            buf_t.setRegion("<-", fSpan, F, stream)
        if B is not None:
            buf_t.setRegion("<-", bSpan, B, stream)

    def fw(self, x, stream, _: "->", y, fw_trace_for_bw):
        self.nn.copyToInput("<-", _fullSpan(x), x, stream)
        self.nn.forward(x, stream, "->", y, fw_trace_for_bw)
        self.nn.copyFromOutput(_fullSpan(y), stream, "->", y)

    def bw(self, fw_trace_for_bw, t, stream, _: "->", buf_tC, buf_mapper):
        self.nn.setTarget("<-", _fullSpan(t), t, stream)
        buf_hp = buf_mapper.nn.getHyperparameters() if buf_mapper is not None else None
        self.nn.backward(fw_trace_for_bw, t, stream, "->", buf_tC, buf_hp)

    # ---- 资源工厂与生命周期委托：上层（UranaSystem）经映射器使用 nn，对 nn 无感知 ----

    def zeroGradient(self, stream, _: "->", gradVec):
        self.nn.zeroGradient(stream, "->", gradVec)

    def createVector(self, size):
        return self.nn.createVector(size)

    def createGradientVector(self, size):
        return self.nn.createGradientVector(size)

    def createFwTraceForBw(self):
        return self.nn.createFwTraceForBw()

    def encodingProfile(self):
        return self.nn.encodingProfile()

    def save(self, folderPath):
        self.nn.save(folderPath)

    def loadVector(self, path):
        return self.nn.loadVector(path)

    def loadGradientVector(self, path):
        return self.nn.loadGradientVector(path)

    def close(self):
        self.nn.close()
