package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.bnn_mapper;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.VisionEncoder;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.InputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.OutputVectorDomain;

/**
 * BNN 可拟合映射器：把"意识域语义向量 ↔ nn 输入/输出缓冲"的装配逻辑实在化（真善美第4条），
 * 使用 BoolVector 载体（与 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.original_mapper.OriginalMapper}
 * 用 FloatVector 载体完全对称）。
 * <p>
 * 与 OriginalMapper 的<b>载体差异</b>（BoolVector vs FloatVector）：
 * <ul>
 *   <li>{@code assembleX} 的 G（boolean[]）：FloatVector 有 {@code setRegion(Span, boolean[], long)} 重载；
 *       BoolVector 无此重载，故走 {@link BoolVector#copyRegionFromHost}（host→device bit 级路径）。</li>
 *   <li>{@code assembleX} 的 dt（long）：FloatVector 有 {@code setRegion(Span, long, long)} 重载；
 *       BoolVector 无此重载，故拆成 64 个 boolean 走 {@link BoolVector#copyRegionFromHost}
 *       （与 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_bnn.AbstractBnnNeuralNetwork#copyToInputFromLong}
 *       同款 bit 编码路径）。</li>
 *   <li>C/F/B（BoolVector）：走 {@link BoolVector#setRegion(Span, BoolVector, long)}（device→device）。</li>
 * </ul>
 * <p>
 * 视觉编码住本包（{@link BitplaneEncoder}，fittable_mapper/bnn_mapper/ 层），与 original_mapper 同款
 * "采集与编码分离"模式（采集在 sensor 侧 glBlit 深拷贝快照，编码在映射器层从快照纹理解码到 BoolVector）。
 * 区别于原初代理的 sensor 侧 VisionOps（采集+编码合一）。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：urana 经本映射器使用 nn，不直接接触 nn 的具体家族模式（BNN 位运算）。</li>
 *   <li><b>第3条</b>：本 mapper 与 OriginalMapper 并列实现 {@link FittableMapper}，
 *       任一可被删换而不改 urana——"上层 ai 系统中的映射器可以切换到 bnn_mapper 也可以切换到 original_mapper"。</li>
 * </ul>
 */
public class BnnMapper implements FittableMapper, AutoCloseable {

    private final INeuralNetwork nn;
    private final InputVectorDomain inputDomain;
    private final OutputVectorDomain outputDomain;

    /** 内部工作缓冲：extractC 的落点（BoolVector，对应伪代码 y.C 的 Java 实在化） */
    private final BoolVector workC;
    /** 内部工作缓冲：extractF 的落点（BoolVector，对应伪代码 y.F 的 Java 实在化） */
    private final BoolVector workF;

    public BnnMapper(INeuralNetwork nn, InputVectorDomain inputDomain, OutputVectorDomain outputDomain) {
        this.nn = nn;
        this.inputDomain = inputDomain;
        this.outputDomain = outputDomain;
        this.workC = new BoolVector(outputDomain.getInheritanceInfoSpan().getLength());
        this.workF = new BoolVector(outputDomain.getFeelingSpan().getLength());
    }

    private static Span fullSpan(VectorBase v) {
        return new Span(0, v.size()) {};
    }

    /**
     * 把 long 拆成 64 个 boolean（BNN bit 编码，与 AbstractBnnNeuralNetwork.copyToInputFromLong 同款）。
     */
    private static boolean[] longToBoolArray(long value) {
        boolean[] bits = new boolean[64];
        for (int i = 0; i < 64; i++) {
            bits[i] = ((value >>> i) & 1L) != 0;
        }
        return bits;
    }

    @Override
    public void assembleX(VectorBase C, VectorBase F, boolean[] G, long dt, long stream /* -> */, VectorBase bufX) {
        Span cSpan = inputDomain.getInheritanceInfoSpan();
        Span fSpan = inputDomain.getFeelingSpan();
        Span gSpan = inputDomain.getTargetTimeOrientationSpan();
        Span dtSpan = inputDomain.getFeelingBehaviorSamplingDtSpan();
        BoolVector buf = (BoolVector) bufX;
        if (C != null) {
            buf.setRegion(/* <- */ cSpan, (BoolVector) C, stream);
        }
        if (F != null) {
            buf.setRegion(/* <- */ fSpan, (BoolVector) F, stream);
        }
        if (G != null) {
            // BoolVector 无 setRegion(Span, boolean[], long) 重载，走 copyRegionFromHost（host→device bit 级）
            buf.copyRegionFromHost(/* <- */ gSpan, G, stream);
        }
        // dt 是 primitive long，不可空——伪代码 if dt is not None 在 Java 退化为恒真
        // BoolVector 无 setRegion(Span, long, long) 重载，拆 64 bit 走 copyRegionFromHost
        buf.copyRegionFromHost(/* <- */ dtSpan, longToBoolArray(dt), stream);
    }

    @Override
    public void assembleT(VectorBase C, VectorBase F, VectorBase B, long stream /* -> */, VectorBase bufT) {
        Span cSpan = outputDomain.getInheritanceInfoSpan();
        Span fSpan = outputDomain.getFeelingSpan();
        Span bSpan = outputDomain.getBehaviorSpan();
        BoolVector buf = (BoolVector) bufT;
        if (C != null) {
            buf.setRegion(/* <- */ cSpan, (BoolVector) C, stream);
        }
        if (F != null) {
            buf.setRegion(/* <- */ fSpan, (BoolVector) F, stream);
        }
        if (B != null) {
            buf.setRegion(/* <- */ bSpan, (BoolVector) B, stream);
        }
    }

    @Override
    public void fw(VectorBase x, long stream /* -> */, VectorBase y, Object fwTraceForBw) {
        nn.copyToInput(/* <- */ fullSpan(x), x, stream);
        nn.forward(x, stream /* -> */, y, fwTraceForBw);
        nn.copyFromOutput(fullSpan(y), stream /* -> */, y);
    }

    @Override
    public void bw(Object fwTraceForBw, VectorBase t, long stream /* -> */, VectorBase bufTc, FittableMapper bufMapper) {
        nn.setTarget(/* <- */ fullSpan(t), t, stream);
        // 经 FittableMapper.getHyperparameters() 接口取 bufHp——不感知 bufMapper 具体家族
        // （真善美第3条：装饰器 mapper 可作为 bufMapper 插入，本实现零改动地适配）。
        Object bufHp = bufMapper != null ? bufMapper.getHyperparameters() : null;
        nn.backward(fwTraceForBw, t, stream /* -> */, bufTc, bufHp);
    }

    /**
     * 对应伪代码 {@code y.C}：从输出向量 y 抽取 C（继承信息）区域。
     * <p>
     * 伪代码的 {@code y.C} 是零拷贝视图（Python 属性访问）。Java VectorBase 无视图机制，
     * 故拷贝到内部 workC 并返回其引用（设计原则第4条：用实在的拷贝把不实在的视图转化为实在的缓冲）。
     * <p>
     * 返回值而非出参：伪代码 {@code y.C} 是属性访问（无出参注入）。若加 bufC 出参则算子签名多出
     * 伪代码没有的参数（违反原则2），故原则2在此优先于原则5的"无返回值"偏好。调用方须立即消费，
     * 下次调用会覆盖 workC 内容（与 y.C 视图在 y 被覆盖后失效同构）。
     */
    @Override
    public VectorBase extractC(VectorBase y, long stream) {
        workC.copyRegionFrom(y, outputDomain.getInheritanceInfoSpan(), fullSpan(workC), stream);
        return workC;
    }

    /**
     * 对应伪代码 {@code y.F}：从输出向量 y 抽取 F（感觉）区域。语义同 {@link #extractC}。
     */
    @Override
    public VectorBase extractF(VectorBase y, long stream) {
        workF.copyRegionFrom(y, outputDomain.getFeelingSpan(), fullSpan(workF), stream);
        return workF;
    }

    // ---- 资源工厂与生命周期委托：上层（UranaSystem）经映射器使用 nn，对 nn 无感知 ----

    @Override
    public void zeroGradient(long stream /* -> */, VectorBase gradVec) {
        nn.zeroGradient(stream /* -> */, gradVec);
    }

    @Override
    public void zeroVector(long stream /* -> */, VectorBase vec) {
        nn.zeroVector(stream /* -> */, vec);
    }

    @Override
    public VectorBase createVector(int size) {
        return nn.createVector(size);
    }

    @Override
    public VectorBase createGradientVector(int size) {
        return nn.createGradientVector(size);
    }

    @Override
    public Object createFwTraceForBw() {
        return nn.createFwTraceForBw();
    }

    // ---- 感觉载体契约：mapper 只透传，载体类型知识留在 nn 家族 ----

    @Override
    public VectorBase newFeelingBuffer() {
        return nn.newFeelingBuffer(inputDomain.getFeelingSpan().getLength());
    }

    @Override
    public VisionEncoder newVisionEncoder() {
        return nn.newVisionEncoder();
    }

    @Override
    public VectorBase newBehaviorBuffer() {
        return nn.newBehaviorBuffer(outputDomain.getBehaviorSpan().getLength());
    }

    @Override
    public void readBehaviorTo(VectorBase behaviorBuffer, int[] dst, long stream) {
        nn.readBehaviorTo(behaviorBuffer, dst, stream);
    }

    @Override
    public Object getHyperparameters() {
        return nn.getHyperparameters();
    }

    @Override
    public InputVectorDomain getInputDomain() {
        return inputDomain;
    }

    @Override
    public OutputVectorDomain getOutputDomain() {
        return outputDomain;
    }

    @Override
    public void save(String folderPath) {
        nn.save(folderPath);
    }

    @Override
    public VectorBase loadVector(String path) {
        return nn.loadVector(path);
    }

    @Override
    public VectorBase loadGradientVector(String path) {
        return nn.loadGradientVector(path);
    }

    @Override
    public void close() throws Exception {
        if (workC != null) workC.close();
        if (workF != null) workF.close();
        nn.close();
    }
}
