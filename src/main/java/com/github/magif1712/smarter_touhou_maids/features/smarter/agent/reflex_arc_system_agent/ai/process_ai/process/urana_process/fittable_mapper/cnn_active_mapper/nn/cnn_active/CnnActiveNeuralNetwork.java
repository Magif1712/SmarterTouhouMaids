package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper.nn.cnn_active;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.cnn.AbstractCnnNeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.cnn.containers.CnnFwTraceForBw;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.cnn.containers.CnnHyperparameters;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.cnn.containers.CnnNetworkData;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.bionic_muscle_effector.semantics.PolarLayout;

/**
 * 新活性 CNN（叶子）：{@link AbstractCnnNeuralNetwork} 的子类，但<b>在照抄件上独立演化</b>
 * （方案 §零 修正一：照抄 mechanism、复用 carrier、冻结 urana 流程）。
 * <p>
 * 与原 CNN（{@code CnnNeuralNetwork}）的差异（方案 §三 差异清单）：
 * <ol>
 *   <li><b>D1/D3 对称零均值初始化</b>——构造期用 {@link CnnActiveInit} 把 {@code q/l/r/b} 覆盖为
 *       {@code (−bound, +bound)}，打破"单边非负 ⟹ z ≥ 0 ⟹ 判决恒真 ⟹ 256 位恒 1 ⟹ 原地不动"
 *       的根因病链。{@code p} 不施加（位置语义）。</li>
 *   <li><b>D2 奇激活</b>——{@code tanh}(默认) / {@code clipped-linear}，θ=0，值域 {@code (−1,1)}。
 *       sigmoid 被裁出 {@code Ext(D)}（无法表示有符号的 C 段 target）。</li>
 *   <li><b>D4 B 段时间递归</b>——{@link #prevB} 自持"上一拍 B 段"，前向的 B 段 {@code l/r} 改读它
 *       （16 位块内模环），前向末回灌。消除原设计 18 位跨域泄漏，并给 B 段补上时间结构。</li>
 *   <li><b>D7 预留位掩 0</b>——{@link #readBehaviorTo} 把 {@code [208,256)} 强制 0，保住冻结位诊断。</li>
 *   <li><b>L0.1 安全不变量</b>（2026-09-14 加入，用于消除运行期发散，见
 *       {@code Document/CNN活性增强-L0发散修复方案.md}）：
 *       <b>I2 C 域投影</b>——{@code tC}（= 流程的 C2 = 继承信息）在唯一产出点被投影回 C 的合法值域
 *       {@code [−1,+1]}（native {@code cnn_active_project_c_domain}，gradient_ops.cu）。它使
 *       {@code |T| ≤ 1} ⟹ {@code |δ| ≤ 4} ⟹ {@code |Δw| ≤ 0.04} ⟹ 权重至多线性增长、不可能溢出；
 *       <b>I5 有限性</b>——非有限 ⟹ 中性（native {@code cnn_active_activate} 的 {@code isfinite} 守卫 +
 *       δ 非有限置 0 + 本类 {@link #readBehaviorTo} 的显式判有限）。它掐断 NaN 经 {@code prevB} 的
 *       逐拍自持通道。</li>
 *   <li><b>L0.2 可用性不变量</b>（同上；I2 之后追加，把"不崩"提升为"可用"）：
 *       <b>I3 权重限幅</b>——{@code q/l/r/b} 更新后投影到 {@code [−W, W]}
 *       （native {@code cnn_active_clamp_weight}）；<b>I4 B 段自环收缩</b>——仅 {@code j >= bOffset}
 *       保 {@code |l_j|+|r_j| ≤ LOOP_GAIN_CAP < 1}（超限则同比例缩）。I2 已保证"不崩"，
 *       但权重仍会线性增长并把 {@code z} 推入饱和（{@code a′ → 0}）⇒ 退化成原 CNN 的饱和墙；
 *       I3/I4 正是为此。三者皆为**不变量**（不可选、不进 GUI）——按推论2，含发散组合的 {@code Ext(D)}
 *       必须被裁剪，裁后等价于不可选，故公理(3) 下不变量严格优于新增可选参数；
 *       数值的权威定义在 native，{@link CnnActiveOptions} 侧只作文档索引。</li>
 * </ol>
 * <p>
 * <b>为什么 D4 由 NN 自己持有 prevB 而不动流程</b>（方案 §1.4）：输入向量没有 B 段，补一段要改
 * {@code assembleX}／输入布局——那是 urana 流程，不能动。而"上一拍输出"在系统里本就有一个等形先例：
 * C 段（{@code UranaFunction.fastTick} 把 {@code state.fastY} 的 C 段回灌进 {@code prospectiveInheritance}）。
 * 差别只是载体归属：C 的载体在输入向量里（流程负责），B 的载体在 NN 里（NN 负责）。
 * <p>
 * <b>三要素与定理1</b>（方案 §四）：{@code A = <X, f, G>}，其中 {@code X ∪ {prevB}} 视为环境条件，
 * 故 {@code f} 仍是单一映射（公理(1) 满足）；{@code Ext(S.Y) ⊆ Ext(T.Y)} 由复用
 * {@link AbstractCnnNeuralNetwork#CNN_PROFILE} 与 B 段 offset {@code 4F}、len 256 保证（定理1(3)）。
 */
public class CnnActiveNeuralNetwork extends AbstractCnnNeuralNetwork {

    /**
     * B 段（行为位）在输出向量中的位置描述符。
     * <p>
     * 取自 {@link PolarLayout#defaultHumanLike()} 的 reservedSpan——"哪些位是预留"这个知识
     * 属于效应器侧的布局描述符（消费者视角），本类不重复定义常量（真善美第3条：单一数据源）。
     * 预留段在 behaviour 子域内的相对位置即 {@code [208, 256)}。
     */
    private static final Span BEHAVIOR_RESERVED = PolarLayout.defaultHumanLike().getReservedSpan();

    /** 分解方式 D（激活 + 初始化半径）；训练/前向/判决均经它取值。 */
    private final CnnActiveOptions options;

    /**
     * B 段的"上一拍"浮点值（256 float），D4 的时间邻域源。
     * <p>
     * 构造期由 {@link FloatVector} 分配并 memset 0 ⟹ 第一拍无递归，优雅退化（方案 §1.4 ①）。
     * 每次 {@link #forward} 末回灌本拍 B 段。截断式单步递归：不做 BPTT（不把梯度回溯到上一拍的 y），
     * 否则要改 {@code GradCellOp} 的签名——那是流程，不能动（方案 §1.5）。
     */
    private final FloatVector prevB;

    public CnnActiveNeuralNetwork(int inputSize, int outputSize, CnnNetworkData networkData, CnnActiveOptions options) {
        super(inputSize, outputSize, networkData);
        this.options = options;
        this.prevB = new FloatVector(CNN_BEHAVIOR_LENGTH);
        // idx/w 的"生产者"归本模块（定理1(2)：不与原 CNN 共享可变演化路径）。
        // 基类构造器已用原 CNN 的 refreshCache 刷过一次（同一纯函数，值相同），此处用本模块自己的
        // refreshCache 再刷一次——一次性开销，换来"生产者与消费者同源"的独立性。
        CnnActiveInferenceOps.cnnActiveRefreshCache(this.networkData.getHyperparameters(), 0L /* -> */);
    }

    /**
     * 全新（未持久化）网络：carrier 构造期的单边非负填充随后被对称填充<b>覆盖</b>（方案 §6.1 注：
     * 一次性开销，无害——这是"不改 carrier"的代价）。
     */
    public static CnnActiveNeuralNetwork createFresh(int inputSize, int outputSize, CnnActiveOptions options) {
        CnnActiveNeuralNetwork nn = new CnnActiveNeuralNetwork(inputSize, outputSize, null, options);
        nn.overwriteSymmetric(options.getInitBoundW(), options.getInitBoundB(), 0L);
        return nn;
    }

    /**
     * 从磁盘加载权重（尺寸由加载的 hp 反推，镜像原 CNN 的 {@code loadFromFile()}）。
     * <p>
     * 加载路径<b>不</b>重做对称初始化——磁盘上的符号域就是我们要保留的演化成果。
     * 激活选择（{@code options}）与权重无关，照常生效。
     */
    public static CnnActiveNeuralNetwork loadFromFile(String folderPath, CnnActiveOptions options) {
        CnnNetworkData net = CnnNetworkData.loadFromFile(folderPath);
        CnnHyperparameters hp = net.getHyperparameters();
        return new CnnActiveNeuralNetwork(hp.getSizeA0(), hp.getSizeA1(), net, options);
    }

    private static Span fullSpan(VectorBase v) {
        return new Span(0, v.size()) {};
    }

    /** B 段在输出向量中的 span（offset {@code 4F}、len 256——定理1(3) 的活动域契约）。 */
    private Span behaviorSpan() {
        return new Span(outputSize - CNN_BEHAVIOR_LENGTH, CNN_BEHAVIOR_LENGTH) {};
    }

    // ==================== D1/D3：对称零均值初始化 ====================

    /**
     * 把 {@code q/l/r}（半径 {@code boundW}）与 {@code b}（半径 {@code boundB}）原地覆盖为
     * {@code (−bound, +bound)} 的对称零均值分布。
     * <p>
     * OO 方法且「this 是出参」：左边出参、右边入参，标记用 {@code <-}（设计原则第5条）。
     * <p>
     * {@code p} <b>不动</b>：{@code p} 是位置（{@code [0, sizeA1)}），对称化会把位置打到负数 ⟹
     * 结构性禁止（方案 §5 裁决2）。{@code q/l/r/b} 不影响 {@code idx/w}（后者只由 {@code p} 派生），
     * 故无需重刷缓存。
     */
    public void overwriteSymmetric(/* <- */ float boundW, float boundB, long stream) {
        CnnHyperparameters hp = networkData.getHyperparameters();
        long baseSeed = System.nanoTime();
        CnnActiveInit.fillSymmetricInPlace(boundW, baseSeed + 1, stream, hp.getQ());
        CnnActiveInit.fillSymmetricInPlace(boundW, baseSeed + 2, stream, hp.getL());
        CnnActiveInit.fillSymmetricInPlace(boundW, baseSeed + 3, stream, hp.getR());
        CnnActiveInit.fillSymmetricInPlace(boundB, baseSeed + 4, stream, hp.getB());
    }

    // ==================== 前向 / 反向（走本模块自己的 ops）====================

    @Override
    public void forward(VectorBase x, long stream /* -> */, VectorBase y, Object fwTraceForBw) {
        CnnHyperparameters hp = networkData.getHyperparameters();
        FloatVector yv = (FloatVector) y;
        // fwTraceForBw 非 null 时 StoreTrace（同时存 z，clipped 的反向需要），null 时 NoTrace（纯推理）
        CnnActiveInferenceOps.cnnActiveForwardLayer(io.getInput().getVector(), hp, prevB, options.getActivationId(), stream /* -> */, yv, (CnnFwTraceForBw) fwTraceForBw);
        // D4 的回灌**不在此处**：写自持 prevB 属"写 nn 自身状态"，按原则6 单独成 commitPrevB（接收者为出参 ⇒ `<-`）。
    }

    /**
     * D4（方案 §1.4 ②）：把本拍 B 段回灌为下一拍的 prevB（NN 自持的"上一拍行为段"）。
     * <p>
     * <b>为何单独成方法</b>（原则6）：本方法写接收者自身的状态 ⇒ 接收者是出参 ⇒ 用 `<-`，且标记落**首部**
     * （显式参数 `y`/`stream` 全为入参）。这样 `forward` 保持"接收者不是出参"（只写注入的 y/trace），
     * 与另两族（原 CNN / BNN）**同字形**，接口契约对每一族都为真。
     */
    public void commitPrevB(/* <- */ VectorBase y, long stream) {
        FloatVector yv = (FloatVector) y;
        // 节奏与 C 段同构（C 段由流程在每步 fw 后更新），差别只是载体归属（B 段的载体在 NN 里）。
        prevB.copyRegionFrom(yv, behaviorSpan(), fullSpan(prevB), stream);
    }

    @Override
    public void backward(VectorBase bufTc, Object bufHp /* <- */, Object fwTraceForBw, VectorBase y, VectorBase t, long stream) {
        CnnHyperparameters hp = networkData.getHyperparameters();
        CnnActiveTrainingOps.cnnActiveBackwardLayer((CnnFwTraceForBw) fwTraceForBw, (FloatVector) y, hp, target.getVector(), prevB, options.getActivationId(), io.getInput().getVector(), CNN_LEARNING_RATE, stream /* -> */, gradients.getDzWorkspace(), gradients.getInputLayerGradient().getVector(), (FloatVector) bufTc, (CnnHyperparameters) bufHp);
    }

    // ==================== D2 + D7：判决 ====================

    /**
     * 行为读取：FloatVector → float[] → 阈值化 → bit-packed int[]。
     * <p>
     * 相对原 CNN 的三处改：
     * <ul>
     *   <li><b>D2 阈值</b>：{@code >= 0.5f} → {@code >= 0.0f}。奇激活 θ=0，与原 sigmoid 的 θ=0.5
     *       <b>逐位等价</b>（方案 §2.1 命题：σ(z)≥0.5 ⟺ z≥0；tanh/clip 过零 ⟹ a(z)≥0 ⟺ z≥0），
     *       故这一改对行为位零差异，effector 接口零改动。</li>
     *   <li><b>D7 预留位掩 0</b>：把 {@code [208,256)} 的 48 位强制 0，保住 PolarLayout 约定的
     *       "冻结位校验"诊断能力（对称初始化会把它们变成 ~50/50）；对动作零影响（从不被 effector 读取）。</li>
     *   <li><b>I5 有限性（L0.1）</b>：{@code Float.isFinite} 显式前置。非有限 ⟹ 0 位 = **声明的**中性行为，
     *       而不是"NaN 比较恒 false"的副作用。它同时让"中毒"与"安静"在诊断上可分辨。</li>
     * </ul>
     * 零 CUDA 调用：纯 host memcpy 读 mapped pinned memory。
     */
    @Override
    public void readBehaviorTo(VectorBase behaviorBuffer, long stream /* -> */, int[] dst) {
        if (!(behaviorBuffer instanceof FloatVector fv)) {
            throw new IllegalArgumentException("CNN-Active readBehaviorTo requires FloatVector");
        }
        int floatCount = fv.size();
        float[] floats = new float[floatCount];
        fv.readMappedToJava(floats, floatCount);

        int reservedOffset = BEHAVIOR_RESERVED.getOffset();
        int reservedEnd = reservedOffset + BEHAVIOR_RESERVED.getLength();

        int wordCount = (floatCount + 31) / 32;
        for (int i = 0; i < wordCount; i++) {
            int word = 0;
            int base = i * 32;
            for (int b = 0; b < 32; b++) {
                int idx = base + b;
                if (idx >= floatCount) break;
                if (idx >= reservedOffset && idx < reservedEnd) continue;   // D7：预留 48 位恒 0
                // I5（L0.1）：**显式**判有限，不再依赖"NaN 参与比较恒为 false"这条 IEEE 副作用——
                // 副作用给出的结果虽然也是 0 位，但它把"数值中毒"报成了"安静"，下游无法分辨
                // （违反真善美第1条"真"）。显式判之后，0 位是**声明的**中性行为：
                // 0 位 ⟹ MuscleGroup.decodeActivation = 0 ⟹ 张力按 (1−α) 衰减到 0 ⟹ 静止。
                if (Float.isFinite(floats[idx]) && floats[idx] >= CnnActiveOptions.THRESHOLD) {  // D2：θ=0
                    word |= (1 << b);
                }
            }
            if (i < dst.length) {
                dst[i] = word;
            }
        }
    }

    // ==================== 生命周期 ====================

    @Override
    public void close() throws Exception {
        if (prevB != null) {
            prevB.close();
        }
        super.close();
    }
}
