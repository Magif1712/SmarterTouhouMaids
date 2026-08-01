package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common.grad;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.IODomain;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽象梯度计算单元 (Abstract Gradient Cell)。
 * <p>
 * 该类为处理一个N步的链式学习任务提供了骨架实现。它将复杂的两阶段（探索-修正）链式循环逻辑完全封装在基类中，
 * 使得子类只需在构造时提供静态配置（如链条长度、每一步的时间方位向量G），即可创建一个全自动的链式处理器。
 * <p>
 * <b>核心设计模式：配置对象 (Configured Object) + 模板方法 (Template Method)</b>
 * <ul>
 *     <li><b>配置对象</b>: 每个 AbstractGradCell 的实例在构造时就被配置为一个特定任务的处理器（例如，“一个处理5步展望的单元”）。
 *         它的核心配置（链长、G向量序列）是不可变的。</li>
 *     <li><b>模板方法</b>: 定义了一个完整的、包含N步链式循环和两阶段学习的算法骨架 {@link #execute(List)}。
 *         子类通过调用父类构造函数来注入其特定配置，从而定制化整个链式处理器的行为。</li>
 * </ul>
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *     <li>持 {@link INeuralNetwork} 接口而非 NetworkData/IO/IOLayerGradients/TargetVector；BNN 资源藏在实现里。</li>
 *     <li>状态向量（grad_C_buffer/fz/state.input/output/c_input_for_phase2 等）类型为 {@link VectorBase}，
 *         由 nn.createVector/createGradientVector 创建——urana 不绑 BNN 载体。</li>
 *     <li>两阶段 BPTT 训练算法（探索零C前向→反向不更新→修正C'前向→反向更新）是<b>通用链式梯度下降</b>，
 *         非 BNN 特定——留 urana 适配器编排。∇C/fz/ChainStepState 是通用训练状态，一并留。</li>
 *     <li>nn 特定转换（negateAndBinarize、清零梯度）走 nn 接口方法，藏在实现里。</li>
 * </ul>
 */
public abstract class AbstractGradCell implements AutoCloseable {

    // --- 核心数据容器 ---
    /** 神经网络的抽象边界。本单元通过接口访问 nn，不感知 BNN/CNN 具体实现。 */
    protected final INeuralNetwork nn;

    // --- 语义域描述符 ---
    /** 定义了输入/输出向量中不同区段（如感觉、行为、继承信息等）的语义和位置。 */
    protected final IODomain ioDomain;

    // --- 实例配置 (构造后不变) ---
    /** 链条的长度 (组数 N)。 */
    protected final int chainLength;
    /** 存储链条中每一步的 G 向量。维度为 [步数][G向量长度]。 */
    protected final boolean[][] gPerStep;

    /** 预分配的样本池，子类通过填充此池来传入目标，无需创建临时对象。 */
    protected final ChainStepSample[] samplePool;

    // --- 状态容器 (在 execute 方法执行期间使用) ---
    /** 存储链条中每一步的内部状态快照。 */
    protected final List<ChainStepState> stepStates;
    /**
     * 一个独立的缓冲区，用于在反向传播链中安全地传递梯度 ∇C。
     * 每次计算出新的 ∇C 后，都会深拷贝到这里，以防在下一次迭代中被覆盖。
     */
    protected final VectorBase grad_C_buffer;
    /** 在第二阶段（修正）中，作为输入向量“继承信息”部分 (C_in) 的向量。它的值由第一阶段的梯度计算而来。 */
    protected final VectorBase c_input_for_phase2;
    /** 一个全为零的向量，用于在第一阶段（探索）中清空输入向量的“继承信息”部分。 */
    protected final VectorBase zero_vector_C;
    protected final VectorBase next_c_input;
    protected final VectorBase current_F_input;

    protected VectorBase terminalGradC; // 跨轮次末端梯度，外部绑定

    /**
     * 主构造函数。
     *
     * @param nn          神经网络抽象边界。
     * @param chainLength 链条的长度 (组数 N)。
     * @param gPerStep    一个二维数组，存储每一步的 G 向量。其第一维长度必须等于 chainLength。
     */
    protected AbstractGradCell(INeuralNetwork nn, int chainLength, boolean[][] gPerStep) {
        if (gPerStep.length != chainLength) {
            throw new IllegalArgumentException("G向量的数量 (" + gPerStep.length + ") 必须等于链条长度 (" + chainLength + ")");
        }
        this.chainLength = chainLength;
        this.gPerStep = gPerStep;

        this.ioDomain = new IODomain();
        this.nn = nn;

        int inputTotalLen = this.ioDomain.getInputDomain().TOTAL_LENGTH;
        int outputTotalLen = this.ioDomain.getOutputDomain().TOTAL_LENGTH;
        int sizeC = this.ioDomain.getInputDomain().getInheritanceInfoSpan().getLength();
        int sizeF = this.ioDomain.getInputDomain().getFeelingSpan().getLength();

        this.stepStates = new ArrayList<>(chainLength);
        for (int i = 0; i < chainLength; i++) {
            this.stepStates.add(new ChainStepState(
                    nn.createVector(inputTotalLen),   // state.input
                    nn.createVector(outputTotalLen),  // state.fz（BNN: BoolVector，CNN: FloatVector，由 nn 决定）
                    nn.createVector(outputTotalLen)   // state.output
            ));
        }

        this.grad_C_buffer = nn.createGradientVector(sizeC);
        // ✅ 确保首轮起点为 0（构造期一次性初始化，NULL 流同步）
        nn.zeroGradient(this.grad_C_buffer, 0L);

        this.c_input_for_phase2 = nn.createVector(sizeC);
        this.next_c_input = nn.createVector(sizeC);
        this.zero_vector_C = nn.createVector(sizeC);
        // 全零向量初始化（BNN: BoolVector.copyRegionFromHost(false[])；CNN: 等价；nn 特定，藏实现）
        nn.zeroVector(this.zero_vector_C, 0L);
        this.current_F_input = nn.createVector(sizeF);

        this.samplePool = new ChainStepSample[chainLength];
        for (int i = 0; i < chainLength; i++) {
            this.samplePool[i] = new ChainStepSample(null, null);
        }
    }

    /**
     * 便利构造函数，用于链条中每一步的 G 向量都相同的情况。
     */
    protected AbstractGradCell(INeuralNetwork nn, int chainLength, boolean[] g) {
        this(nn, chainLength, repeatGVector(g, chainLength));
    }

    private static boolean[][] repeatGVector(boolean[] g, int n) {
        boolean[][] result = new boolean[n][];
        for (int i = 0; i < n; i++) {
            result[i] = g;
        }
        return result;
    }

    public void bindTerminalGradientBuffer(VectorBase externalBuffer) {
        this.terminalGradC = externalBuffer;
    }

    /**
     * 模板方法：执行完整的N步链式两阶段学习流程。
     *
     * @param initialInputF 链条第一步的输入 F。
     * @param dtMillis      时间间隔（毫秒）。dt 语义在 urana，编码由 nn 做。
     * @param samples       包含N步数据的列表。
     * @param stream        CUDA 流句柄。
     */
    public final VectorBase execute(VectorBase initialInputF, long dtMillis, List<ChainStepSample> samples, long stream) {
        if (samples.size() != this.chainLength) {
            throw new IllegalArgumentException("传入的数据样本数 (" + samples.size() + ") 必须与Cell配置的链条长度 (" + this.chainLength + ") 相匹配");
        }
        for (int i = 0; i < samples.size(); i++) {
            ChainStepSample s = samples.get(i);
            this.samplePool[i].set(s.target_F, s.target_B);
        }
        return executeWithPool(initialInputF, dtMillis, stream);
    }

    /**
     * 执行完整的 N 步链式训练，并自动管理跨轮次梯度闭环。
     * 子类在组装好 samples 后调用此方法即可，无需手动处理 ∇C 的绑定与回写。
     */
    protected final void executeWithClosedLoop(VectorBase initialInputF, long dtMillis, long stream) {
        this.bindTerminalGradientBuffer(this.grad_C_buffer);
        this.executeWithPool(initialInputF, dtMillis, stream);
    }

    private VectorBase executeWithPool(VectorBase initialInputF, long dtMillis, long stream) {
        if (this.samplePool[0].target_F == null) {
            throw new IllegalStateException("samplePool 未填充，子类须在调用 executeWithClosedLoop 前填充 target");
        }
        // === 阶段一：探索 (不更新权重) ===
        runForwardPass(initialInputF, this.zero_vector_C, dtMillis, stream);
        VectorBase initialGradC = runBackwardPass(this.samplePool, false, stream);

        // === 阶段二：修正与学习 (更新权重) ===
        // BNN: negateAndBinarize(∇C)→C'；CNN: 等价转换。nn 特定，藏实现。
        nn.gradientToInput(initialGradC, this.c_input_for_phase2, stream);
        runForwardPass(initialInputF, this.c_input_for_phase2, dtMillis, stream);
        return runBackwardPass(this.samplePool, true, stream);
    }

    // ==================== 共享实现 (两阶段学习) ====================

    private void runForwardPass(VectorBase initial_F, VectorBase initial_C, long dtMillis, long stream) {
        var inputDomain = this.ioDomain.getInputDomain();
        var feelingSpan = inputDomain.getFeelingSpan();
        var gSpan = inputDomain.getTargetTimeOrientationSpan();
        var dtSpan = inputDomain.getFeelingBehaviorSamplingDtSpan();
        var cSpan_in = inputDomain.getInheritanceInfoSpan();
        var fSpan_out = this.ioDomain.getOutputDomain().getFeelingSpan();
        var cSpan_out = this.ioDomain.getOutputDomain().getInheritanceInfoSpan();

        this.current_F_input.copyRegionFrom(initial_F, fullSpan(initial_F), fullSpan(this.current_F_input), stream);
        VectorBase current_c_input = initial_C;

        for (int i = 0; i < this.chainLength; i++) {
            ChainStepState state = this.stepStates.get(i);

            // 1. 组装输入向量（区域读写全走 nn 接口）
            nn.copyToInput(feelingSpan, this.current_F_input, stream);
            nn.copyToInputFromHost(gSpan, this.gPerStep[i], stream);
            nn.copyToInputFromLong(dtSpan, dtMillis, stream);
            nn.copyToInput(cSpan_in, current_c_input, stream);

            // 2. 执行单步前向传播 (训练前向，存 fz 到 state.fz)
            nn.forwardForTraining(state.fz, stream);

            // 3. 深拷贝必要状态到 ChainStepState
            nn.copyFromInput(fullSpan(state.input), state.input, stream);
            nn.copyFromOutput(fullSpan(state.output), state.output, stream);

            // 4. 提取 C_out 和 F_out 给下一步
            nn.copyFromOutput(cSpan_out, this.next_c_input, stream);
            current_c_input = this.next_c_input;
            nn.copyFromOutput(fSpan_out, this.current_F_input, stream);
        }
    }

    private VectorBase runBackwardPass(ChainStepSample[] samples, boolean updateWeights, long stream) {
        var inputDomain = this.ioDomain.getInputDomain();
        var fSpan_out = this.ioDomain.getOutputDomain().getFeelingSpan();
        var bSpan_out = this.ioDomain.getOutputDomain().getBehaviorSpan();
        var cSpan_out = this.ioDomain.getOutputDomain().getInheritanceInfoSpan();
        VectorBase grad_C_from_next = this.grad_C_buffer;

        // 优化：当 terminalGradC 指向自身时，跳过无意义的自拷贝
        if (this.terminalGradC != null && this.terminalGradC != grad_C_from_next) {
            grad_C_from_next.copyRegionFrom(this.terminalGradC, fullSpan(this.terminalGradC), fullSpan(grad_C_from_next), stream);
        } else if (this.terminalGradC == null) {
            nn.zeroGradient(grad_C_from_next, stream);
        }

        for (int i = this.chainLength - 1; i >= 0; i--) {
            ChainStepState state = this.stepStates.get(i);
            ChainStepSample sample = samples[i];

            prepareBackward(state.output, sample.target_F, sample.target_B, grad_C_from_next, stream, fSpan_out, bSpan_out, cSpan_out);

            try {
                if (updateWeights) {
                    nn.backwardAndUpdate(state.fz, state.input, stream);
                } else {
                    nn.backward(state.fz, stream);
                }
            } catch (Exception e) {
                throw new RuntimeException(
                    "Backward pass failed at step " + i + " in " + getClass().getSimpleName(), e);
            }

            // 3. 关键：深拷贝 ∇C
            nn.copyFromInputGradient(cSpan_out, this.grad_C_buffer, stream);
        }
        return grad_C_from_next;
    }

    private void prepareBackward(VectorBase current_output, VectorBase target_F, VectorBase target_B,
                                 VectorBase grad_C_input_from_prev_cycle, long stream,
                                 Span fSpan_out, Span bSpan_out, Span cSpan_out) {
        // 1. 组装目标向量 y（nn 内部持 target）
        nn.setTarget(fSpan_out, target_F, stream);
        nn.setTarget(bSpan_out, target_B, stream);

        // 2. 分别计算 F 和 B 部分的梯度
        nn.computeOutputGradient(current_output, fSpan_out, stream);
        nn.computeOutputGradient(current_output, bSpan_out, stream);

        // 3. 直接注入来自下一个步骤的 C 区间梯度 (∇C_in)
        nn.injectOutputGradient(cSpan_out, grad_C_input_from_prev_cycle, stream);
    }

    /**
     * 创建一个覆盖向量整个长度的 Span。
     */
    protected Span fullSpan(VectorBase vector) {
        return new Span(0, vector.size()) {};
    }

    /**
     * 释放所有由该单元管理的本地资源。
     */
    @Override
    public void close() throws Exception {
        if (stepStates != null) {
            for (ChainStepState state : stepStates) {
                if (state != null) state.close();
            }
        }
        if (grad_C_buffer != null) grad_C_buffer.close();
        if (c_input_for_phase2 != null) c_input_for_phase2.close();
        if (zero_vector_C != null) zero_vector_C.close();
        if (next_c_input != null) next_c_input.close();
        if (current_F_input != null) current_F_input.close();
    }
}
