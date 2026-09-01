from core import Span
from urana_process.semantics.containers.io.subspan.feeling_span import FeelingSpan
from urana_process.semantics.containers.io.subspan.behavior_span import BehaviorSpan
from urana_process.semantics.containers.io.subspan.inheritance_info_span import InheritanceInfoSpan
from urana_process.semantics.containers.io.subspan.target_time_orientation_span import TargetTimeOrientationSpan
from urana_process.semantics.containers.io.subspan.feeling_behavior_sampling_dt_span import FeelingBehaviorSamplingDtSpan


INHERITANCE_MULTIPLIER = 3
TIME_ORIENTATION_COUNT = 4


class InputVectorDomain:
    def __init__(self, profile):
        cLen = profile.feelingLength * INHERITANCE_MULTIPLIER
        fLen = profile.feelingLength
        gLen = profile.timeOrientationUnitLength * TIME_ORIENTATION_COUNT
        dtLen = profile.dtLength
        self._totalLength = cLen + fLen + gLen + dtLen

        currentOffset = 0
        self.inheritanceInfoSpan = InheritanceInfoSpan(currentOffset, cLen)
        currentOffset += cLen
        self.feelingSpan = FeelingSpan(currentOffset, fLen)
        currentOffset += fLen
        self.targetTimeOrientationSpan = TargetTimeOrientationSpan(currentOffset, gLen)
        currentOffset += gLen
        self.feelingBehaviorSamplingDtSpan = FeelingBehaviorSamplingDtSpan(currentOffset, dtLen)

    def totalLength(self):
        return self._totalLength

    def getFeelingSpan(self):
        return self.feelingSpan

    def getFeelingBehaviorSamplingDtSpan(self):
        return self.feelingBehaviorSamplingDtSpan

    def getInheritanceInfoSpan(self):
        return self.inheritanceInfoSpan

    def getTargetTimeOrientationSpan(self):
        return self.targetTimeOrientationSpan


class OutputVectorDomain:
    def __init__(self, profile):
        cLen = profile.feelingLength * INHERITANCE_MULTIPLIER
        fLen = profile.feelingLength
        bLen = profile.behaviorLength
        self._totalLength = cLen + fLen + bLen

        currentOffset = 0
        self.inheritanceInfoSpan = InheritanceInfoSpan(currentOffset, cLen)
        currentOffset += cLen
        self.feelingSpan = FeelingSpan(currentOffset, fLen)
        currentOffset += fLen
        self.behaviorSpan = BehaviorSpan(currentOffset, bLen)

    def totalLength(self):
        return self._totalLength

    def getFeelingSpan(self):
        return self.feelingSpan

    def getBehaviorSpan(self):
        return self.behaviorSpan

    def getInheritanceInfoSpan(self):
        return self.inheritanceInfoSpan


class IODomain:
    def __init__(self, profile):
        self.inputDomain = InputVectorDomain(profile)
        self.outputDomain = OutputVectorDomain(profile)

    def getInputDomain(self):
        return self.inputDomain

    def getOutputDomain(self):
        return self.outputDomain