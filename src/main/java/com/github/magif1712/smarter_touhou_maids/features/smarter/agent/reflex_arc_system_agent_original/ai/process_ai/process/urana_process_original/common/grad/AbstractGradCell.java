package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.common.grad;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.semantics.containers.io.IODomain;

import java.io.File;
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
 *     <li>状态向量（grad_C_buffer/fz/state.input_original/output/c_input_for_phase2 等）类型为 {@link VectorBase}，
 *         由 nn.createVector/createGradientVector 创建——urana 不绑 BNN 载体。</li>
 *     <li>两阶段 BPTT 训练算法（探索零C前向→反向不更新→修正C'前向→反向更新）是<b>通用链式梯度下降</b>，
 *         非 BNN 特定——留 urana 适配器编排。∇C/fz/ChainStepState 是通用训练状态，一并留。</li>
 *     <li>nn 特定转换（negateAndBinarize、清零梯度）走 nn 接口方法，藏在实现里。</li>
 * </ul>
 */
public abstract class AbstractGradCell implements AutoCloseable {

    // --- 核心数据容器 ---
    /** 神经网络的抽象边界。本单元通过接口访问 nn，不感知 BNN/CNN 具体实现。 */
    protected final INeuralNetwork nn_original;

    // --- 语义域描述符 ---
    /** 定义了输入/输出向量中不同区段（如感觉、行为、继承信息等）的语义和位置。 */
    protected final IODomain ioDomain_original;

    // --- 实例配置 (构造后不变) ---
    /** 链条的长度 (组数 N)。 */
    protected final int chainLength_original;
    /** 存储链条中每一步的 G 向量。维度为 [步数][G向量长度]。 */
    protected final boolean[][] gPerStep_original;

    /** 预分配的样本池，子类通过填充此池来传入目标，无需创建临时对象。 */
    protected final ChainStepSample[] samplePool_original;

    // --- 状态容器 (在 execute 方法执行期间使用) ---
    /** 存储链条中每一步的内部状态快照。 */
    protected final List<ChainStepState> stepStates_original;
    /**
     * 跨轮次持存末端梯度 T_prev：两阶段的终端步都读它作为 ∇C_out，
     * 只在阶段2结束后被 S2 回写一次（由 executeWithPool 末尾统一回写）。
     * <p>
     * 轮内瞬态梯度（组内链条、跨阶段 S1）不落此缓冲区，直读 nn 内部输入层梯度。
     */
    protected final VectorBase grad_C_buffer_original;
    /** 在第二阶段（修正）中，作为输入向量"继承信息"部分 (C_in) 的向量。它的值由第一阶段的梯度计算而来。 */
    protected final VectorBase c_input_for_phase2_original;
    /** 一个全为零的向量，用于在第一阶段（探索）中清空输入向量的"继承信息"部分。 */
    protected final VectorBase zero_vector_C_original;
    protected final VectorBase next_c_input_original;
    protected final VectorBase current_F_input_original;

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
        this.chainLength_original = chainLength;
        this.gPerStep_original = gPerStep;

        this.ioDomain_original = new IODomain(nn.encodingProfile());
        this.nn_original = nn;

        int inputTotalLen = this.ioDomain_original.getInputDomain().totalLength();
        int outputTotalLen = this.ioDomain_original.getOutputDomain().totalLength();
        int sizeC = this.ioDomain_original.getInputDomain().getInheritanceInfoSpan().getLength();
        int sizeF = this.ioDomain_original.getInputDomain().getFeelingSpan().getLength();

        this.stepStates_original = new ArrayList<>(chainLength);
        for (int i = 0; i < chainLength; i++) {
            this.stepStates_original.add(new ChainStepState(
                    nn.createVector(inputTotalLen),   // state.input_original
                    nn.createVector(outputTotalLen),  // state.fz_original（BNN: BoolVector，CNN: FloatVector，由 nn 决定）
                    nn.createVector(outputTotalLen)   // state.output_original
            ));
        }

        this.grad_C_buffer_original = nn.createGradientVector(sizeC);
        // ✅ 确保首轮起点为 0（构造期一次性初始化，NULL 流同步）
        nn.zeroGradient(this.grad_C_buffer_original, 0L);

        this.c_input_for_phase2_original = nn.createVector(sizeC);
        this.next_c_input_original = nn.createVector(sizeC);
        this.zero_vector_C_original = nn.createVector(sizeC);
        // 全零向量初始化（BNN: BoolVector.copyRegionFromHost(false[])；CNN: 等价；nn 特定，藏实现）
        nn.zeroVector(this.zero_vector_C_original, 0L);
        this.current_F_input_original = nn.createVector(sizeF);

        this.samplePool_original = new ChainStepSample[chainLength];
        for (int i = 0; i < chainLength; i++) {
            this.samplePool_original[i] = new ChainStepSample(null, null);
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

    /**
     * 模板方法：执行完整的N步链式两阶段学习流程。
     *
     * @param initialInputF 链条第一步的输入 F。
     * @param dtMillis      时间间隔（毫秒）。dt 语义在 urana，编码由 nn 做。
     * @param samples       包含N步数据的列表。
     * @param stream        CUDA 流句柄。
     */
    public final VectorBase execute(VectorBase initialInputF, long dtMillis, List<ChainStepSample> samples, long stream) {
        if (samples.size() != this.chainLength_original) {
            throw new IllegalArgumentException("传入的数据样本数 (" + samples.size() + ") 必须与Cell配置的链条长度 (" + this.chainLength_original + ") 相匹配");
        }
        for (int i = 0; i < samples.size(); i++) {
            ChainStepSample s = samples.get(i);
            this.samplePool_original[i].set(s.target_F_original, s.target_B_original);
        }
        return executeWithPool(initialInputF, dtMillis, stream);
    }

    /**
     * 执行完整的 N 步链式训练，并自动管理跨轮次梯度闭环。
     * 子类在组装好 samples 后调用此方法即可，无需手动处理 ∇C 的绑定与回写。
     * <p>
     * 闭环机制：grad_C_buffer 是跨轮次持存末端梯度 T_prev，两阶段的终端步都读它，
     * 只在阶段2结束后被 S2 回写一次。轮内瞬态梯度（组内链条、跨阶段 S1）不落缓冲区，
     * 直读 nn 内部输入层梯度。
     */
    protected final void executeWithClosedLoop(VectorBase initialInputF, long dtMillis, long stream) {
        this.executeWithPool(initialInputF, dtMillis, stream);
    }

    private VectorBase executeWithPool(VectorBase initialInputF, long dtMillis, long stream) {
        if (this.samplePool_original[0].target_F_original == null) {
            throw new IllegalStateException("samplePool 未填充，子类须在调用 executeWithClosedLoop 前填充 target");
        }
        var cSpan_out = this.ioDomain_original.getOutputDomain().getInheritanceInfoSpan();

        // === 阶段一：探索 (不更新权重) ===
        runForwardPass(initialInputF, this.zero_vector_C_original, dtMillis, stream);
        runBackwardPass(this.samplePool_original, false, stream);
        // S1 留在 nn 内部输入层梯度中，不落缓冲区；grad_C_buffer 仍持 T_prev

        // === 阶段二：修正与学习 (更新权重) ===
        // C' = transform(S1)：直读 nn 内部输入层梯度（瞬态，无缓冲区）。
        // BNN: negateAndBinarize(∇C 区间)→C'；CNN: 等价转换。nn 特定，藏实现。
        nn_original.gradientToInputFromInternal(cSpan_out, this.c_input_for_phase2_original, stream);
        runForwardPass(initialInputF, this.c_input_for_phase2_original, dtMillis, stream);
        runBackwardPass(this.samplePool_original, true, stream);

        // 阶段2结束：S2 → grad_C_buffer（供下轮 T_prev）。全轮唯一一次写入 grad_C_buffer。
        nn_original.copyFromInputGradient(cSpan_out, this.grad_C_buffer_original, stream);
        return this.grad_C_buffer_original;
    }

    // ==================== 共享实现 (两阶段学习) ====================

    private void runForwardPass(VectorBase initial_F, VectorBase initial_C, long dtMillis, long stream) {
        var inputDomain = this.ioDomain_original.getInputDomain();
        var feelingSpan = inputDomain.getFeelingSpan();
        var gSpan = inputDomain.getTargetTimeOrientationSpan();
        var dtSpan = inputDomain.getFeelingBehaviorSamplingDtSpan();
        var cSpan_in = inputDomain.getInheritanceInfoSpan();
        var fSpan_out = this.ioDomain_original.getOutputDomain().getFeelingSpan();
        var cSpan_out = this.ioDomain_original.getOutputDomain().getInheritanceInfoSpan();

        this.current_F_input_original.copyRegionFrom(initial_F, fullSpan(initial_F), fullSpan(this.current_F_input_original), stream);
        VectorBase current_c_input = initial_C;

        for (int i = 0; i < this.chainLength_original; i++) {
            ChainStepState state = this.stepStates_original.get(i);

            // 1. 组装输入向量（区域读写全走 nn 接口）
            nn_original.copyToInput(feelingSpan, this.current_F_input_original, stream);
            nn_original.copyToInputFromHost(gSpan, this.gPerStep_original[i], stream);
            nn_original.copyToInputFromLong(dtSpan, dtMillis, stream);
            nn_original.copyToInput(cSpan_in, current_c_input, stream);

            // 2. 执行单步前向传播 (训练前向，存 fz 到 state.fz_original)
            nn_original.forwardForTraining(state.fz_original, stream);

            // 3. 深拷贝必要状态到 ChainStepState
            nn_original.copyFromInput(fullSpan(state.input_original), state.input_original, stream);
            nn_original.copyFromOutput(fullSpan(state.output_original), state.output_original, stream);

            // 4. 提取 C_out 和 F_out 给下一步
            nn_original.copyFromOutput(cSpan_out, this.next_c_input_original, stream);
            current_c_input = this.next_c_input_original;
            nn_original.copyFromOutput(fSpan_out, this.current_F_input_original, stream);
        }
    }

    private void runBackwardPass(ChainStepSample[] samples, boolean updateWeights, long stream) {
        var fSpan_out = this.ioDomain_original.getOutputDomain().getFeelingSpan();
        var bSpan_out = this.ioDomain_original.getOutputDomain().getBehaviorSpan();
        var cSpan_out = this.ioDomain_original.getOutputDomain().getInheritanceInfoSpan();

        for (int i = this.chainLength_original - 1; i >= 0; i--) {
            ChainStepState state = this.stepStates_original.get(i);
            ChainStepSample sample = samples[i];

            // 1. 目标与 F/B 梯度（终端与非终端共用）
            nn_original.setTarget(fSpan_out, sample.target_F_original, stream);
        nn_original.setTarget(bSpan_out, sample.target_B_original, stream);
            nn_original.computeOutputGradient(state.output_original, fSpan_out, stream);
            nn_original.computeOutputGradient(state.output_original, bSpan_out, stream);

            // 2. 链式 ∇C_out 注入：
            //    终端步（i == chainLength-1）读跨轮持存缓冲区 grad_C_buffer（T_prev）；
            //    非终端步直读 nn 内部输入层梯度（瞬态组内链条，无缓冲区）。
            if (i == this.chainLength_original - 1) {
                nn_original.injectOutputGradient(cSpan_out, this.grad_C_buffer_original, stream);
            } else {
                nn_original.injectOutputGradientFromInputGradient(cSpan_out, stream);
            }

            // 3. 反向传播
            try {
                if (updateWeights) {
                    nn_original.backwardAndUpdate(state.fz_original, state.input_original, stream);
                } else {
                    nn_original.backward(state.fz_original, stream);
                }
            } catch (Exception e) {
                throw new RuntimeException(
                    "Backward pass failed at step " + i + " in " + getClass().getSimpleName(), e);
            }
            // 不再每步回写 grad_C_buffer：阶段1不触碰 T_prev，S2 由 executeWithPool 末尾统一回写。
        }
    }

    /**
     * 创建一个覆盖向量整个长度的 Span。
     */
    protected Span fullSpan(VectorBase vector) {
        return new Span(0, vector.size()) {};
    }

    // ==================== 持久化（∇C 跨轮梯度缓冲）====================

    /**
     * 从磁盘加载 grad_C_buffer（用 nn.loadGradientVector，nn 知载体类型）。
     * <p>
     * <b>load/save 对称</b>（C2/C3）：save 由 {@link #save} 写磁盘，load 由 nn 知载体类型造新实例
     * 再拷入 grad_C_buffer。文件缺失时保持构造期零值（优雅降级：首启动/存档损坏）。
     * <p>
     * <b>NULL 流</b>：load 在 create 后、awaken 前调用（fast/slow 工作线程尚未启动），
     * 用 NULL 流（0L）做一次性 D2D 拷贝，与构造期 {@code nn.zeroGradient(grad_C_buffer, 0L)} 一致，
     * 无并发风险。
     *
     * @param uranaPath urana 层持久化目录（{@code slot.layerPath("urana")}）
     * @param cellId    本 cell 的标识（如 "prospective"/"retrospective"/"introspective"），
     *                  文件名为 {@code <cellId>_grad_c_original.bin}
     */
    public void load(String uranaPath, String cellId) {
        File f = new File(uranaPath, cellId + "_grad_c_original.bin");
        if (!f.exists()) return; // 文件缺失保持构造期零值
        VectorBase loaded = nn_original.loadGradientVector(f.getAbsolutePath());
        try {
            grad_C_buffer_original.copyRegionFrom(loaded, fullSpan(loaded), fullSpan(grad_C_buffer_original), 0L);
        } finally {
            try { loaded.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * 将 grad_C_buffer 序列化到磁盘（与 {@link #load} 对称）。
     * <p>
     * <b>调用方须先停止工作线程</b>：save 读 GPU 缓冲区做 D2H，若慢环工作线程并发写 grad_C_buffer
     * 会撕裂。UranaSystem.save 在 D2H 前已 join 慢环线程，故本方法无竞争。
     *
     * @param uranaPath urana 层持久化目录
     * @param cellId    本 cell 的标识，文件名为 {@code <cellId>_grad_c_original.bin}
     */
    public void save(String uranaPath, String cellId) {
        grad_C_buffer_original.save(new File(uranaPath, cellId + "_grad_c_original.bin").getAbsolutePath());
    }

    /**
     * 释放所有由该单元管理的本地资源。
     */
    @Override
    public void close() throws Exception {
        if (stepStates_original != null) {
            for (ChainStepState state : stepStates_original) {
                if (state != null) state.close();
            }
        }
        if (grad_C_buffer_original != null) grad_C_buffer_original.close();
        if (c_input_for_phase2_original != null) c_input_for_phase2_original.close();
        if (zero_vector_C_original != null) zero_vector_C_original.close();
        if (next_c_input_original != null) next_c_input_original.close();
        if (current_F_input_original != null) current_F_input_original.close();
    }
}
