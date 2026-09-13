package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.common.inference;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.semantics.containers.io.IODomain;

/**
 * 抽象推理单元。与 AbstractGradCell 同构，负责执行 N 步前向推理链条。
 * 子类仅配置 chainLength 和 gPerStep，基类全自动管理 F/C 的跨步传递。
 *
 * <p>设计原则（真善美）：
 * <ul>
 *   <li>持 {@link INeuralNetwork} 接口引用而非 NetworkData/IO；BNN 资源藏在实现里。</li>
 *   <li>状态向量（next_c_input/current_F_input/resultBuffer）类型为 {@link VectorBase}，
 *       由 nn.createVector 创建——urana 不绑 BNN 的 BoolVector 载体。</li>
 *   <li>区域读写通过 nn 接口方法（copyToInput/copyFromOutput/copyToInputFromLong），
 *       不直接持 nn 内部向量引用。</li>
 *   <li>dt 语义在 urana：execute 接收 dtMillis，内部调 nn.copyToInputFromLong(dtSpan, dtMillis, stream)。
 *       nn 只做机械编码，不知道 dt 含义。</li>
 * </ul>
 *
 * 内存策略：预分配 resultBuffer，execute 返回内部引用（调用方须立即消费）。
 */
public abstract class AbstractInferenceCell implements AutoCloseable {

    protected final INeuralNetwork nn_original;
    protected final IODomain ioDomain_original;
    protected final int chainLength_original;
    protected final boolean[][] gPerStep_original;

    protected final VectorBase next_c_input_original;
    protected final VectorBase current_F_input_original;
    /** 预分配的结果缓冲区，execute 返回此对象的引用 */
    protected final VectorBase resultBuffer_original;

    protected AbstractInferenceCell(INeuralNetwork nn, int chainLength, boolean[][] gPerStep) {
        if (gPerStep.length != chainLength) {
            throw new IllegalArgumentException("G向量数量 (" + gPerStep.length +
                    ") 必须等于链条长度 (" + chainLength + ")");
        }
        this.chainLength_original = chainLength;
        this.gPerStep_original = gPerStep;
        this.nn_original = nn;
        this.ioDomain_original = new IODomain(nn.encodingProfile());

        int sizeC = this.ioDomain_original.getInputDomain().getInheritanceInfoSpan().getLength();
        this.next_c_input_original = nn.createVector(sizeC);
        int sizeF = this.ioDomain_original.getInputDomain().getFeelingSpan().getLength();
        this.current_F_input_original = nn.createVector(sizeF);
        int sizeOut = this.ioDomain_original.getOutputDomain().totalLength();
        this.resultBuffer_original = nn.createVector(sizeOut);
    }

    protected AbstractInferenceCell(INeuralNetwork nn, int chainLength, boolean[] g) {
        this(nn, chainLength, repeatGVector(g, chainLength));
    }

    private static boolean[][] repeatGVector(boolean[] g, int n) {
        boolean[][] result = new boolean[n][];
        for (int i = 0; i < n; i++) {
            result[i] = g;
        }
        return result;
    }

    /**
     * 执行 N 步推理链条。
     *
     * @param initialF 链条第一步的 F（感觉输入）
     * @param initialC 链条第一步的 C（继承信息）
     * @param dtMillis 时间间隔（毫秒）。dt 语义在 urana，编码由 nn 做。
     * @param stream   CUDA 流句柄（专用 uranaStream，使 kernel 与视觉采集流并发，避免 NULL 流串行卡顿）
     * @return 最终输出向量（内部 resultBuffer 的引用，调用方须立即消费，不可长期持有）
     */
    public final VectorBase execute(VectorBase initialF, VectorBase initialC, long dtMillis, long stream) {
        // dt 语义在 urana：urana 知道 dtSpan 是 dt 区段、dtMillis 是时间间隔；nn 只做机械编码。
        var dtSpan = this.ioDomain_original.getInputDomain().getFeelingBehaviorSamplingDtSpan();
        var feelingSpan = this.ioDomain_original.getInputDomain().getFeelingSpan();
        var gSpan = this.ioDomain_original.getInputDomain().getTargetTimeOrientationSpan();
        var cSpan_in = this.ioDomain_original.getInputDomain().getInheritanceInfoSpan();
        var fSpan_out = this.ioDomain_original.getOutputDomain().getFeelingSpan();
        var cSpan_out = this.ioDomain_original.getOutputDomain().getInheritanceInfoSpan();

        this.current_F_input_original.copyRegionFrom(initialF, fullSpan(initialF), fullSpan(this.current_F_input_original), stream);
        VectorBase current_c_input = initialC;

        for (int i = 0; i < this.chainLength_original; i++) {
            // 组装输入向量（区域读写全走 nn 接口）
            nn_original.copyToInput(feelingSpan, this.current_F_input_original, stream);
            nn_original.copyToInputFromHost(gSpan, this.gPerStep_original[i], stream);
            nn_original.copyToInputFromLong(dtSpan, dtMillis, stream);
            nn_original.copyToInput(cSpan_in, current_c_input, stream);

            // 前向传播（推理不需要 fz）
            nn_original.forward(stream);

            // 提取下一步的 F_out 和 C_out
            nn_original.copyFromOutput(cSpan_out, this.next_c_input_original, stream);
            current_c_input = this.next_c_input_original;
            nn_original.copyFromOutput(fSpan_out, this.current_F_input_original, stream);
        }

        // 深拷贝最终结果到预分配缓冲区（避免返回 io 内部视图，防止下一轮覆盖）
        nn_original.copyFromOutput(fullSpan(this.resultBuffer_original), this.resultBuffer_original, stream);
        return this.resultBuffer_original;
    }

    protected Span fullSpan(VectorBase vector) {
        return new Span(0, vector.size()) {
        };
    }

    @Override
    public void close() throws Exception {
        if (next_c_input_original != null)
            next_c_input_original.close();
        if (current_F_input_original != null)
            current_F_input_original.close();
        if (resultBuffer_original != null)
            resultBuffer_original.close();
    }
}
