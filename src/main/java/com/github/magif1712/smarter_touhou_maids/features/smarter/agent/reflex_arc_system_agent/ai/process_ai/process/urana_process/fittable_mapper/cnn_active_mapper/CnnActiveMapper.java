package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.VisionEncoder;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper.nn.cnn_active.CnnActiveNeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.original_mapper.RgbFloatEncoder;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.InputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.OutputVectorDomain;

/**
 * 新活性 CNN 的映射器：照抄 {@code OriginalMapper} 的装配逻辑（自包含，不与原类共享演化路径）。
 * <p>
 * <b>为什么照抄而不是复用</b>（方案 §零 修正一 / §六 划分规则）：映射器属 mechanism 一侧的装配层，
 * "照抄件是我们自己的"，可在拷贝件上任意改（如未来在图与 nn 之间插入归一化）。复用原类则会让两者
 * 共享可变演化路径，违反"自包含的 module"这一关键判断（定理1(2)）。
 * <p>
 * <b>照抄项</b>：{@code assembleX}/{@code assembleT} 的区域装配、{@code fw}/{@code bw} 的驱动顺序、
 * {@code extractC}/{@code extractF} 的内部工作缓冲、资源工厂与生命周期的逐层委托。
 * <b>复用项</b>（carrier，无 CNN 决策逻辑）：{@link RgbFloatEncoder}（视觉解码器：Texture → FloatVector RGB，
 * 与 CNN-Active 的浮点载体一致）、{@link #newFeelingBuffer()} 等经 nn 家族透传的载体契约。
 * <p>
 * 方法体方向标记用 /* -&gt; *&#47; 注释（设计原则第5条）。
 */
public class CnnActiveMapper implements FittableMapper, AutoCloseable {

    private final INeuralNetwork nn;
    private final InputVectorDomain inputDomain;
    private final OutputVectorDomain outputDomain;

    /** 内部工作缓冲：extractC 的落点（对应伪代码 y.C 的 Java 实在化） */
    private final FloatVector workC;
    /** 内部工作缓冲：extractF 的落点（对应伪代码 y.F 的 Java 实在化） */
    private final FloatVector workF;

    public CnnActiveMapper(INeuralNetwork nn, InputVectorDomain inputDomain, OutputVectorDomain outputDomain) {
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
        // forward 后 y 即为前向结果（DPS 契约：三族 nn.forward 都只写注入的 y，无需 copyFromOutput 中转）
        // D4 的时间递归（方案 §1.4）：把本拍 B 段回灌为己物 prevB —— 那一步**写 nn 自身状态**，
        // 故按原则6 单独成方法 commitPrevB（OO 且接收者为出参 ⇒ `<-`）；节奏与 C 段同构（C 段由流程在每步 fw 后更新）。
        ((CnnActiveNeuralNetwork) nn).commitPrevB(y, stream);
    }

    @Override
    public void bw(VectorBase bufTc, FittableMapper bufMapper /* <- */, Object fwTraceForBw, VectorBase y, VectorBase t, long stream) {
        nn.setTarget(/* <- */ fullSpan(t), t, stream);
        // 经 FittableMapper.getHyperparameters() 接口取 bufHp——不感知 bufMapper 具体家族
        // （真善美第3条：装饰器 mapper 可作为 bufMapper 插入，本实现零改动地适配）。
        Object bufHp = bufMapper != null ? bufMapper.getHyperparameters() : null;
        nn.backward(bufTc, bufHp /* <- */, fwTraceForBw, y, t, stream);
    }

    /**
     * 对应伪代码 {@code y.C}：从输出向量 y 抽取 C（继承信息）区域。
     * <p>
     * Java VectorBase 无视图机制，故拷贝到内部 workC 并返回其引用（设计原则第4条）。
     * 调用方须立即消费，下次调用会覆盖 workC 内容。
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

    /**
     * 视觉解码器：复用 {@link RgbFloatEncoder}（carrier）。
     * <p>
     * 它的输出是 FloatVector RGB——与本模块的浮点载体（对称权重 + 奇激活）天然一致；
     * 复制一份纯属熵增（公理(3)：三要素相同则熵低者更优）。原 CNN 与本模块共享的是"RGB → 浮点的编码约定"，
     * 这是载体契约（定理1(3) 的一部分），不是可变的 mechanism。
     */
    @Override
    public VisionEncoder newVisionEncoder() {
        return new RgbFloatEncoder();
    }

    @Override
    public VectorBase newBehaviorBuffer() {
        return nn.newBehaviorBuffer(outputDomain.getBehaviorSpan().getLength());
    }

    @Override
    public void readBehaviorTo(VectorBase behaviorBuffer, long stream /* -> */, int[] dst) {
        nn.readBehaviorTo(behaviorBuffer, stream /* -> */, dst);
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
