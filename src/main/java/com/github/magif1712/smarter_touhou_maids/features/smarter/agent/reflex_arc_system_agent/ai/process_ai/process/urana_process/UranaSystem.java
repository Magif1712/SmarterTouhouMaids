package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.core.execution.MappedGenerationBuffer;
import com.github.magif1712.smarter_touhou_maids.core.execution.event.Event;
import com.github.magif1712.smarter_touhou_maids.core.execution.stream.Stream;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.IProcessSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common.AncSlider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 运行环境壳（照搬伪代码 {@code urana_system.py}）：线程、节流、dt 测量、定时存档、生命周期。
 * <p>
 * 算法过程（{@link UranaFunction#fastTick}/{@link UranaFunction#slowTick}）零持有；
 * 算法状态在 {@link UranaState}，由本类持有并注入——环境保管状态，过程只算。
 * <p>
 * 快环 dt 用慢环最新测量的 slowDtMillis（伪代码设计：快环不自测 dt，复用慢环的 dt）。
 * <p>
 * <b>依赖接口</b>（真善美第2条）：mapper 字段为 {@link FittableMapper} 接口类型——
 * 运行环境壳只经接口使用映射器（save/loadVector/getInputDomain/getOutputDomain/close），
 * 不感知具体 mapper 家族。附属模组可插入实现 {@link FittableMapper} 的装饰器层
 * （如日志/量化/蒸馏）到 process→nn 之间，UranaSystem 零改动地适配。
 */
public class UranaSystem implements IProcessSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger("UranaSystem");

    private final FittableMapper mapper;
    private final long fastMinDtMillis;
    private final long slowMinDtMillis;

    private final Stream uranaStream;
    private final Stream fastStream;
    private final UranaState state;

    private volatile Event visionEvent;
    private boolean dtDebugEnabled = false;

    private volatile boolean running = false;
    private boolean closed = false;

    private long lastPeriodicSaveMs = 0;
    private LongSupplier periodicSaveIntervalProvider = () -> 0L;
    private Supplier<SaveSlot> periodicSaveSlotProvider = () -> null;
    private Runnable periodicPostSaveAction = () -> {};

    private volatile VectorBase currentFeeling;
    private MappedGenerationBuffer behaviorChannel;

    /** 慢环最新测量的 dt（毫秒），快环复用之。volatile：慢环写、快环读。 */
    private volatile long slowDtMillis = 0;

    private Thread fastWorkerThread;
    private Thread slowWorkerThread;

    public UranaSystem(FittableMapper mapper, long fastMinDtMillis, long slowMinDtMillis) {
        this.mapper = mapper;
        this.fastMinDtMillis = fastMinDtMillis;
        this.slowMinDtMillis = slowMinDtMillis;
        this.uranaStream = new Stream();
        this.fastStream = new Stream();
        this.state = new UranaState(mapper);
    }

    public void setPeriodicSaveConfig(/* <- */ Supplier<SaveSlot> slotProvider, LongSupplier intervalProvider, Runnable postSaveAction) {
        this.periodicSaveSlotProvider = slotProvider != null ? slotProvider : () -> null;
        this.periodicSaveIntervalProvider = intervalProvider != null ? intervalProvider : () -> 0L;
        this.periodicPostSaveAction = postSaveAction != null ? postSaveAction : () -> {};
    }

    private void maybePeriodicSave() {
        long now = System.currentTimeMillis();
        if (lastPeriodicSaveMs == 0) {
            lastPeriodicSaveMs = now;
        }
        long intervalMillis = periodicSaveIntervalProvider.getAsLong();
        if (intervalMillis > 0 && now - lastPeriodicSaveMs >= intervalMillis) {
            lastPeriodicSaveMs = now;
            try {
                SaveSlot snapshotSlot = periodicSaveSlotProvider.get();
                if (snapshotSlot != null) {
                    saveToDisk(snapshotSlot);
                    periodicPostSaveAction.run();
                }
            } catch (Exception e) {
                LOGGER.warn("[Urana] periodic snapshot save failed", e);
            }
        }
    }

    private void saveToDisk(SaveSlot slot) {
        String nnPath = slot.layerPath("nn");
        String uranaPath = slot.layerPath("urana");
        new File(uranaPath).mkdirs();

        try {
            mapper.save(nnPath);
        } catch (Exception e) {
            LOGGER.warn("[Urana] save nn failed", e);
        }

        UranaState s = state;
        saveSlider(s.prospectiveAncSlider, uranaPath, "prospective");
        saveSlider(s.retrospectiveAncSlider, uranaPath, "retrospective");
        saveSlider(s.introspectiveAncSlider, uranaPath, "introspective");

        saveVector(s.prospectiveInheritance, uranaPath, "prospective_inheritance.bin");
        saveVector(s.retrospectiveInheritance, uranaPath, "retrospective_inheritance.bin");
        saveVector(s.introspectiveInheritance, uranaPath, "introspective_inheritance.bin");
        saveVector(s.prospectiveTC, uranaPath, "prospective_tC.bin");
        saveVector(s.retrospectiveTC, uranaPath, "retrospective_tC.bin");
        saveVector(s.introspectiveTC, uranaPath, "introspective_tC.bin");
    }

    private void saveSlider(AncSlider slider, String uranaPath, String name) {
        try {
            slider.save(uranaPath, name);
        } catch (Exception e) {
            LOGGER.warn("[Urana] save anc slider failed: {}", name, e);
        }
    }

    private void saveVector(VectorBase vector, String uranaPath, String fileName) {
        try {
            vector.save(new File(uranaPath, fileName).getAbsolutePath());
        } catch (Exception e) {
            LOGGER.warn("[Urana] save state vector failed: {}", fileName, e);
        }
    }

    @Override
    public void save(SaveSlot slot) {
        if (slot == null) return;
        stopWorkersForSave(/* <- */);
        saveToDisk(slot);
    }

    private void stopWorkersForSave() {
        if (!running) return;
        running = false;
        joinWorker(fastWorkerThread);
        joinWorker(slowWorkerThread);
    }

    public void load(/* <- */ SaveSlot slot) {
        if (slot == null) return;
        UranaState s = state;
        String uranaPath = slot.layerPath("urana");

        s.prospectiveAncSlider.load(/* <- */ uranaPath, "prospective");
        s.retrospectiveAncSlider.load(/* <- */ uranaPath, "retrospective");
        s.introspectiveAncSlider.load(/* <- */ uranaPath, "introspective");

        loadVector(s.prospectiveInheritance, uranaPath, "prospective_inheritance.bin", false);
        loadVector(s.retrospectiveInheritance, uranaPath, "retrospective_inheritance.bin", false);
        loadVector(s.introspectiveInheritance, uranaPath, "introspective_inheritance.bin", false);
        loadVector(s.prospectiveTC, uranaPath, "prospective_tC.bin", true);
        loadVector(s.retrospectiveTC, uranaPath, "retrospective_tC.bin", true);
        loadVector(s.introspectiveTC, uranaPath, "introspective_tC.bin", true);
    }

    private void loadVector(VectorBase target, String uranaPath, String fileName, boolean isGradient) {
        File f = new File(uranaPath, fileName);
        if (!f.exists()) return;
        VectorBase loaded = isGradient
                ? mapper.loadGradientVector(f.getAbsolutePath())
                : mapper.loadVector(f.getAbsolutePath());
        try {
            target.copyRegionFrom(/* <- */ loaded, fullSpan(loaded), fullSpan(target), 0L);
        } finally {
            try { loaded.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void awaken(/* <- */ VectorBase feelingBuffer, Event visionEvent, MappedGenerationBuffer behaviorChannel) {
        if (running) return;
        this.currentFeeling = feelingBuffer;
        this.visionEvent = visionEvent;
        this.behaviorChannel = behaviorChannel;
        this.running = true;
        this.fastWorkerThread = new Thread(this::runFastLoop, "UranaFastWorker");
        this.slowWorkerThread = new Thread(this::runSlowLoop, "UranaSlowWorker");
        this.fastWorkerThread.setDaemon(true);
        this.slowWorkerThread.setDaemon(true);
        this.fastWorkerThread.start();
        this.slowWorkerThread.start();
    }

    @Override
    public void shutdown(/* <- */) {
        if (running) {
            stopWorkersForSave(/* <- */);
            fastWorkerThread = null;
            slowWorkerThread = null;
        }
        if (closed) return;
        closed = true;
        try {
            close();
        } catch (Exception e) {
            LOGGER.error("[Urana] resource release error", e);
        }
    }

    private void joinWorker(Thread t) {
        if (t == null) return;
        try {
            t.join(1500);
            if (t.isAlive()) {
                t.join(500);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void runFastLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            long now = System.nanoTime();
            if (dtDebugEnabled) {
                LOGGER.info("[UranaFast] dt={} ms", slowDtMillis);
            }
            try {
                UranaFunction.fastTick(mapper, state, currentFeeling, slowDtMillis, visionEvent, fastStream /* -> */, behaviorChannel, state);
            } catch (Exception e) {
                LOGGER.error("[Urana][Fast] runFastTick error", e);
            }
            throttle(fastMinDtMillis, now);
        }
    }

    private void runSlowLoop() {
        long lastRunStartNanos = 0;
        while (running && !Thread.currentThread().isInterrupted()) {
            long now = System.nanoTime();
            long dtMillis = (lastRunStartNanos == 0) ? 0 : (now - lastRunStartNanos) / 1_000_000L;
            lastRunStartNanos = now;
            slowDtMillis = dtMillis;

            if (dtDebugEnabled) {
                LOGGER.info("[UranaSlow] dt={} ms", dtMillis);
            }

            try {
                UranaFunction.slowTick(mapper, state, dtMillis, uranaStream /* -> */, state, mapper);
            } catch (Exception e) {
                LOGGER.error("[Urana][Slow] runSlowTick error", e);
            }

            maybePeriodicSave(/* <- */);
            throttle(slowMinDtMillis, lastRunStartNanos);
        }
    }

    private void throttle(long minDtMillis, long lastRunStartNanos) {
        if (minDtMillis > 0) {
            long elapsedNanos = System.nanoTime() - lastRunStartNanos;
            long remainingNanos = minDtMillis * 1_000_000L - elapsedNanos;
            if (remainingNanos > 0) {
                try {
                    Thread.sleep(remainingNanos / 1_000_000L, (int) (remainingNanos % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Override
    public void setDtDebugEnabled(/* <- */ boolean enabled) {
        this.dtDebugEnabled = enabled;
    }

    @Override
    public int feelingSize() {
        return mapper.getInputDomain().getFeelingSpan().getLength();
    }

    @Override
    public int behaviorSize() {
        return mapper.getOutputDomain().getBehaviorSpan().getLength();
    }

    private Span fullSpan(VectorBase v) {
        return new Span(0, v.size()) {};
    }

    @Override
    public void close() throws Exception {
        UranaState s = state;
        // 反序释放：滑轨 → 向量 → 映射器 → 流
        if (s.prospectiveAncSlider != null) s.prospectiveAncSlider.close();
        if (s.retrospectiveAncSlider != null) s.retrospectiveAncSlider.close();
        if (s.introspectiveAncSlider != null) s.introspectiveAncSlider.close();

        if (s.prospectiveInheritance != null) s.prospectiveInheritance.close();
        if (s.retrospectiveInheritance != null) s.retrospectiveInheritance.close();
        if (s.introspectiveInheritance != null) s.introspectiveInheritance.close();
        if (s.prospectiveTC != null) s.prospectiveTC.close();
        if (s.retrospectiveTC != null) s.retrospectiveTC.close();
        if (s.introspectiveTC != null) s.introspectiveTC.close();

        // 工作草稿（fastBufC/fastBufF/slowBufC 已移入 mapper 内部，由 mapper.close() 释放）
        if (s.fastY != null) s.fastY.close();
        if (s.fastBufX != null) s.fastBufX.close();
        if (s.slowYs != null) {
            for (VectorBase v : s.slowYs) {
                if (v != null) v.close();
            }
        }
        if (s.slowBufX != null) s.slowBufX.close();
        if (s.buf_t != null) s.buf_t.close();
        if (s.pushEvent != null) {
            try { s.pushEvent.close(); } catch (Exception ignored) {}
        }

        if (mapper != null) mapper.close();
        if (uranaStream != null) {
            try { uranaStream.close(); } catch (Exception ignored) {}
        }
        if (fastStream != null) {
            try { fastStream.close(); } catch (Exception ignored) {}
        }
    }
}
