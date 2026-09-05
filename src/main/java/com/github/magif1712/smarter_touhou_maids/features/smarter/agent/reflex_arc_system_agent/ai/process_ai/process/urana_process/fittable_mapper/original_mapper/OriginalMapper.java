package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.original_mapper;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.VisionEncoder;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.InputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.OutputVectorDomain;

/**
 * Urana 可拟合映射器：把"意识域语义向量 ↔ nn 输入/输出缓冲"的装配逻辑实在化（真善美第4条）。
 * <p>
 * urana（意识域）经本映射器使用 nn：assembleX/assembleT 把 C/F/G/dt、C/F/B 装配进缓冲区，
 * fw/bw 驱动 nn 前向/反向，zeroGradient 清零。urana 不直接接触 nn 的具体家族模式（真善美第2条）。
 * <p>
 * 方法体照搬伪代码 {@code urana_fittable_mapper.py}，方向标记用 /* -&gt; *&#47; 注释（设计原则第5条）。
 * <p>
 * 设计原则第2条对 Java 静态类型的 adaptations：
 * <ul>
 *   <li>{@code assembleX} 的四路装配统一走 {@link FloatVector#setRegion} 重载——
 *       boolean[]/long 的转换在 setRegion 内部完成，伪代码的鸭子类型 setRegion 用 Java 重载实在化。</li>
 *   <li>{@link #extractC} / {@link #extractF} 对应伪代码 {@code y.C}/{@code y.F}（零拷贝视图）。
 *       Java VectorBase 无视图机制，故用内部工作缓冲 + 区域拷贝实在化（设计原则第4条）。
 *       工作缓冲藏于映射器内部，算子签名与伪代码一致（无 buf_c/buf_f 出参）。</li>
 * </ul>
 * <p>
 * bw 中 {@code bufMapper} cast 到 {@link OriginalMapper} 访问其 {@code nn} 字段（伪代码
 * {@code buf_mapper.nn} 的 duck-typing 翻译）。资源工厂与生命周期方法委托 nn（urana 对 nn 无感知）。
 * <p>
 * {@code close()} 真实释放 nn + 内部工作缓冲（伪代码 {@code self.nn.close()}）。
 */
public class OriginalMapper implements FittableMapper, AutoCloseable {

    private final INeuralNetwork nn;
    private final InputVectorDomain inputDomain;
    private final OutputVectorDomain outputDomain;

    /** 内部工作缓冲：extractC 的落点（对应伪代码 y.C 的 Java 实在化） */
    private final FloatVector workC;
    /** 内部工作缓冲：extractF 的落点（对应伪代码 y.F 的 Java 实在化） */
    private final FloatVector workF;

    public OriginalMapper(INeuralNetwork nn, InputVectorDomain inputDomain, OutputVectorDomain outputDomain) {
        this.nn = nn;
        this.inputDomain = inputDomain;
        this.outputDomain = outputDomain;
        this.workC = new FloatVector(outputDomain.getInheritanceInfoSpan().getLength());
        this.workF = new FloatVector(outputDomain.getFeelingSpan().getLength());
    }

    private static Span fullSpan(VectorBase v) {
        return new Span(0, v.size()) {};
    }

    @Override
    public void assembleX(VectorBase C, VectorBase F, boolean[] G, long dt, long stream /* -> */, VectorBase bufX) {
        Span cSpan = inputDomain.getInheritanceInfoSpan();
        Span fSpan = inputDomain.getFeelingSpan();
        Span gSpan = inputDomain.getTargetTimeOrientationSpan();
        Span dtSpan = inputDomain.getFeelingBehaviorSamplingDtSpan();
        FloatVector buf = (FloatVector) bufX;
        if (C != null) {
            buf.setRegion(/* <- */ cSpan, (FloatVector) C, stream);
        }
        if (F != null) {
            buf.setRegion(/* <- */ fSpan, (FloatVector) F, stream);
        }
        if (G != null) {
            buf.setRegion(/* <- */ gSpan, G, stream);
        }
        // dt 是 primitive long，不可空——伪代码 if dt is not None 在 Java 退化为恒真
        buf.setRegion(/* <- */ dtSpan, dt, stream);
    }

    @Override
    public void assembleT(VectorBase C, VectorBase F, VectorBase B, long stream /* -> */, VectorBase bufT) {
        Span cSpan = outputDomain.getInheritanceInfoSpan();
        Span fSpan = outputDomain.getFeelingSpan();
        Span bSpan = outputDomain.getBehaviorSpan();
        FloatVector buf = (FloatVector) bufT;
        if (C != null) {
            buf.setRegion(/* <- */ cSpan, (FloatVector) C, stream);
        }
        if (F != null) {
            buf.setRegion(/* <- */ fSpan, (FloatVector) F, stream);
        }
        if (B != null) {
            buf.setRegion(/* <- */ bSpan, (FloatVector) B, stream);
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

    public VectorBase createVector(int size) {
        return nn.createVector(size);
    }

    public VectorBase createGradientVector(int size) {
        return nn.createGradientVector(size);
    }

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
        return new RgbFloatEncoder();
    }

    @Override
    public VectorBase newBehaviorBuffer() {
        return nn.newBehaviorBuffer(outputDomain.getBehaviorSpan().getLength());
    }

    @Override
    public void readBehaviorTo(VectorBase behaviorBuffer, int[] dst, long stream) {
        nn.readBehaviorTo(behaviorBuffer, dst, stream);
    }

    /**
     * 取 nn 超参数（bufHp）。bw 经 {@link FittableMapper#getHyperparameters()} 接口取 bufMapper 的 hp，
     * 故本方法是 bw 跨实例访问 bufHp 的实在化入口。装饰器 mapper 应转发到被装饰的 mapper。
     */
    @Override
    public Object getHyperparameters() {
        return nn.getHyperparameters();
    }

    /**
     * 暴露 inputDomain/outputDomain 供上层（UranaState/UranaSystem/算法函数）访问 span 布局。
     * 照搬伪代码 {@code mapper.inputDomain}/{@code mapper.outputDomain} 的直接属性访问。
     * <p>
     * 装饰器 mapper 应转发本方法到被装饰的 mapper（链路上的最终 mapper）。
     */
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