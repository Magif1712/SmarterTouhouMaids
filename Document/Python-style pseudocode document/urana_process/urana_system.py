import time
import threading
import os

from core import Span, Stream
from urana_process.urana_function import fast_tick, slow_tick
from urana_process.urana_state import UranaState


def _fullSpan(vector):
    return Span(0, vector.size())


class UranaSystem:
    """运行环境壳：线程、节流、dt 测量、定时存档、生命周期。

    算法过程（fast_tick/slow_tick）在 urana_function.py，零持有；
    算法状态在 UranaState，由本类持有并注入——环境保管状态，过程只算。
    """

    def __init__(self, mapper, fastMinDtMillis=0, slowMinDtMillis=0):
        self.mapper = mapper
        self.inputDomain = mapper.inputDomain
        self.outputDomain = mapper.outputDomain
        self.fastMinDtMillis = fastMinDtMillis
        self.slowMinDtMillis = slowMinDtMillis

        self.uranaStream = Stream()
        self.fastStream = Stream()

        self.state = UranaState(mapper)

        self.visionEvent = None
        self.dtDebugEnabled = False

        self.running = False
        self.closed = False

        self.lastPeriodicSaveMs = 0
        self.periodicSaveIntervalProvider = lambda: 0
        self.periodicSaveSlotProvider = lambda: None
        self.periodicPostSaveAction = lambda: None

        self.currentFeeling = None
        self.behaviorChannel = None

        self.slowDtMillis = 0

        self.fastWorkerThread = None
        self.slowWorkerThread = None

    def setPeriodicSaveConfig(self, _: "<-", slotProvider, intervalProvider, postSaveAction):
        self.periodicSaveSlotProvider = slotProvider if slotProvider is not None else lambda: None
        self.periodicSaveIntervalProvider = intervalProvider if intervalProvider is not None else lambda: 0
        self.periodicPostSaveAction = postSaveAction if postSaveAction is not None else lambda: None

    def _maybePeriodicSave(self, _: "<-"):
        now = int(time.time() * 1000)
        if self.lastPeriodicSaveMs == 0:
            self.lastPeriodicSaveMs = now
        intervalMillis = self.periodicSaveIntervalProvider()
        if intervalMillis > 0 and now - self.lastPeriodicSaveMs >= intervalMillis:
            self.lastPeriodicSaveMs = now
            try:
                snapshotSlot = self.periodicSaveSlotProvider()
                if snapshotSlot is not None:
                    self._saveToDisk(snapshotSlot)
                    self.periodicPostSaveAction()
            except Exception as e:
                print(f"[Urana] periodic snapshot save failed: {e}")

    def _saveToDisk(self, slot):
        nnPath = slot.layerPath("nn")
        uranaPath = slot.layerPath("urana")

        try:
            self.mapper.save(nnPath)
        except Exception as e:
            print(f"[Urana] save nn failed: {e}")

        state = self.state
        for name, slider in (("prospective", state.prospectiveAncSlider), ("retrospective", state.retrospectiveAncSlider), ("introspective", state.introspectiveAncSlider)):
            try:
                slider.save(uranaPath, name)
            except Exception as e:
                print(f"[Urana] save anc slider failed: {name}, {e}")

        vectors = (("prospective_inheritance", state.prospectiveInheritance), ("retrospective_inheritance", state.retrospectiveInheritance), ("introspective_inheritance", state.introspectiveInheritance), ("prospective_tC", state.prospectiveTC), ("retrospective_tC", state.retrospectiveTC), ("introspective_tC", state.introspectiveTC))
        for name, vector in vectors:
            try:
                vector.save(f"{uranaPath}/{name}.bin")
            except Exception as e:
                print(f"[Urana] save state vector failed: {name}, {e}")

    def save(self, slot):
        if slot is None:
            return
        self._stopWorkersForSave("<-")
        self._saveToDisk(slot)

    def _stopWorkersForSave(self, _: "<-"):
        if not self.running:
            return
        self.running = False
        self._joinWorker(self.fastWorkerThread)
        self._joinWorker(self.slowWorkerThread)

    def load(self, _: "<-", slot):
        if slot is None:
            return
        state, uranaPath = self.state, slot.layerPath("urana")

        for name, slider in (("prospective", state.prospectiveAncSlider), ("retrospective", state.retrospectiveAncSlider), ("introspective", state.introspectiveAncSlider)):
            slider.load("<-", uranaPath, name)

        loaders = (("prospective_inheritance", state.mapper.loadVector, state.prospectiveInheritance), ("retrospective_inheritance", state.mapper.loadVector, state.retrospectiveInheritance), ("introspective_inheritance", state.mapper.loadVector, state.introspectiveInheritance), ("prospective_tC", state.mapper.loadGradientVector, state.prospectiveTC), ("retrospective_tC", state.mapper.loadGradientVector, state.retrospectiveTC), ("introspective_tC", state.mapper.loadGradientVector, state.introspectiveTC))
        for name, loader, target in loaders:
            f = os.path.join(uranaPath, f"{name}.bin")
            if not os.path.exists(f):
                continue  # 文件缺失时保持构造期默认（tC=0、inheritance=未初始化），优雅降级
            loaded = loader(f)
            try:
                target.copyRegionFrom("<-", loaded, _fullSpan(loaded), _fullSpan(target), None)
            finally:
                try:
                    loaded.close()
                except Exception:
                    pass

    def awaken(self, _: "<-", feelingBuffer, visionEvent, behaviorChannel):
        if self.running:
            return
        self.currentFeeling = feelingBuffer
        self.visionEvent = visionEvent
        self.behaviorChannel = behaviorChannel
        self.running = True
        self.fastWorkerThread = threading.Thread(target=self._runFastLoop, name="UranaFastWorker", daemon=True)
        self.slowWorkerThread = threading.Thread(target=self._runSlowLoop, name="UranaSlowWorker", daemon=True)
        self.fastWorkerThread.start()
        self.slowWorkerThread.start()

    def shutdown(self, _: "<-"):
        if self.running:
            self._stopWorkersForSave("<-")
            self.fastWorkerThread = None
            self.slowWorkerThread = None
        if self.closed:
            return
        self.closed = True
        try:
            self._close()
        except Exception as e:
            print(f"[Urana] resource release error: {e}")

    def _joinWorker(self, t):
        if t is None:
            return
        try:
            t.join(1.5)
            if t.is_alive():
                t.join(0.5)
        except Exception:
            pass

    def _runFastLoop(self):
        while self.running:
            now = time.time_ns()
            if self.dtDebugEnabled:
                print(f"[UranaFast] dt={self.slowDtMillis} ms")
            try:
                fast_tick(self.mapper, self.state, self.currentFeeling, self.slowDtMillis, self.visionEvent, self.fastStream, "->", self.behaviorChannel, self.state)
            except Exception as e:
                print(f"[Urana][Fast] runFastTick error: {e}")
            self._throttle(self.fastMinDtMillis, now)

    def _runSlowLoop(self):
        lastRunStartNanos = 0
        while self.running:
            now = time.time_ns()
            dtMillis = (now - lastRunStartNanos) // 1_000_000 if lastRunStartNanos != 0 else 0
            lastRunStartNanos = now
            self.slowDtMillis = dtMillis

            if self.dtDebugEnabled:
                print(f"[UranaSlow] dt={dtMillis} ms")

            try:
                slow_tick(self.mapper, self.state, dtMillis, self.uranaStream, "->", self.state, self.mapper)
            except Exception as e:
                print(f"[Urana][Slow] runSlowTick error: {e}")

            self._maybePeriodicSave("<-")

            self._throttle(self.slowMinDtMillis, lastRunStartNanos)

    def _throttle(self, minDtMillis, lastRunStartNanos):
        if minDtMillis > 0:
            elapsedNanos = time.time_ns() - lastRunStartNanos
            remainingNanos = minDtMillis * 1_000_000 - elapsedNanos
            if remainingNanos > 0:
                try:
                    time.sleep(remainingNanos / 1_000_000_000)
                except Exception:
                    pass

    def setDtDebugEnabled(self, _: "<-", enabled):
        self.dtDebugEnabled = enabled

    def _close(self):
        state = self.state
        for slider in state.prospectiveAncSlider, state.retrospectiveAncSlider, state.introspectiveAncSlider:
            slider.close()
        for v in state.prospectiveInheritance, state.retrospectiveInheritance, state.introspectiveInheritance, state.prospectiveTC, state.retrospectiveTC, state.introspectiveTC:
            v.close()

        self.mapper.close()
        self.uranaStream.close()
        self.fastStream.close()
