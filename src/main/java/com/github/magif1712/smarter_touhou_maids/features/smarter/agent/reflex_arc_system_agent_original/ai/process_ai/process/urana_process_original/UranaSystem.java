package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.IProcessSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.semantics.containers.io.InputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.semantics.containers.io.OutputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.semantics.containers.io.subspan.BehaviorSpan;
import com.github.magif1712.smarter_touhou_maids.core.execution.MappedGenerationBuffer;
import com.github.magif1712.smarter_touhou_maids.core.execution.event.Event;
import com.github.magif1712.smarter_touhou_maids.core.execution.stream.Stream;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.subsystem.introspective.IntrospectiveAnchor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.subsystem.introspective.context.IntrospectiveInference;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.subsystem.introspective.context.TrainingContext;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.subsystem.prospective.ProspectiveAnchor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.subsystem.prospective.context.ProspectiveGradCell;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.subsystem.prospective.context.ProspectiveInference;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.subsystem.retrospective.RetrospectiveAnchor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.subsystem.retrospective.context.RetrospectiveGradCell;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.subsystem.retrospective.context.RetrospectiveInference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Urana 系统的顶层协调器。
 * <p>
 * 意识域 C 的模式：意识体有<b>两个节律</b>——
 * <ul>
 *   <li><b>快反应环</b>（行动者）：感知当下 → 行动者推理 → 产出行为。高频、反射性。</li>
 *   <li><b>慢巩固环</b>（学习/反思）：复盘 → 梯度下降 → 反思。低频、巩固性。</li>
 * </ul>
 * 代码域 D 用双线程双流对齐这两个节律（真善美第 2 条：C 有两个节律，D 也应有两个；原单线程单流
 * 把两节律 fused 成一是错配）。
 * <p>
 * <b>快环</b>（{@link #runFastLoop} + {@link #fastStream}）：流程二（行动者推理）直接吃
 * {@code currentFeeling}（不经流程一的中转，因为流程一已移到慢环），产出 behavior 宣告给效应器，
 * 并把 (输入感觉, 输出行为, fastDt) 三元组快照到 trace 三缓冲供慢环消费。prospectiveInheritance_original（行动者
 * 工作记忆）由快环独占维护。
 * <p>
 * <b>慢环</b>（{@link #runSlowLoop} + {@link #uranaStream}）：流程一（感觉-行为滑动窗口）从 trace
 * 三缓冲取最新一份成对 push 进 anchor（保证同一列 feeling/behavior 同时刻对齐），随后跑流程
 * 四/五/三/六/七/八。学习/反思在慢环按自身节律连续积累（不因快环高频而离散）。
 * <p>
 * <b>权重争用</b>（方案 C）：快环 forward 读权重、慢环梯度下降写权重，并发跑在两流上。安全性依赖
 * CUDA 对齐 32 位写的原子性——快环读永远看到 old 或 new，不撕裂、不越界、不崩。更新窗口内快环读到
 * "部分更新"的权重，对 BNN 反应控制等价于轻微噪声，下一轮即恢复。该不变量在 native
 * {@code backwardGradientDescentLayer} 处文档化（真善美第 3 条：把并发安全这个不实在的约束用
 * 文档化的硬件不变量实在化）。
 * <p>
 * <b>NN 抽象边界</b>（真善美第 2/3 条）：本类持 {@link INeuralNetwork} 接口引用（非具体 BNN 实现），
 * 状态向量类型为 {@link VectorBase}（由 nn.createVector 创建）。换 NN 实现（BNN→CNN）时
 * UranaSystem 零改动——nn 实现可替换，urana 不感知载体类型与权重结构。
 * dt 语义在 urana（urana 知道 dtSpan 是 dt 区段、dtMillis 是时间间隔），nn 只做机械编码
 * （{@link INeuralNetwork#copyToInputFromLong}）。
 * <p>
 * <b>流程系统抽象边界</b>（真善美第 2/3 条）：本类实现 {@link IProcessSystem} 接口，对外周
 * （SmarterClientService）只暴露感觉/行为/启停/序列化/诊断/尺寸通用契约。urana 的双环节律、
 * 三层子系统、anchor/inference/gradcell、span 语义、痕迹三缓冲等模式藏在实现里。换流程系统
 * （urana→别的）时，外周运行期零改动——SmarterClientService 依赖 IProcessSystem 接口，不依赖本类。
 * 与 NN 抽象边界形成对称结构：nn 层（INeuralNetwork）和流程层（IProcessSystem）各自可替换。
 * <p>
 * 遵循“真善美”设计原则，此类作为整个 urana AI 功能的唯一入口点。
 */
public class UranaSystem implements IProcessSystem {

    // === 语义布局（构造注入；profile 已下沉到 nn，domain 只持 urana 的布局 + 倍数关系）===
    private final InputVectorDomain inputDomain_original;
    private final OutputVectorDomain outputDomain_original;

    // === 共享网络（抽象接口，可替换实现）===
    private final INeuralNetwork nn_original;

    /**
     * 慢环专用 CUDA 流（重用原 uranaStream）。
     * <p>
     * 慢环的重计算 kernel（学习/反思）与 D2D/H2D 拷贝均跑在此流上，与快环 {@link #fastStream} 分离，
     * 两环可并发占用 GPU。vision→快环 的跨流数据可见性由 {@link #visionEvent} 显式同步。
     */
    private final Stream uranaStream;

    /**
     * 快环专用 CUDA 流。
     * <p>
     * 快环的行动者推理（流程二 forward）跑在此流上，与慢环学习 kernel 并发，使 behavior 高频产出
     * 不被慢环的长时学习阻塞。两环共享 {@link #nn} 权重，并发安全见类注释（方案 C）。
     */
    private final Stream fastStream;

    /**
     * 视觉采集完成事件（由 SmarterClientService 注入，非 Urana 所有）。
     * <p>
     * 视觉 kernel 在 cudaStream 上写完 feelingBuffer 后 record 此 event；
     * <b>快环</b>在每轮 runFastTick 开头 waitEvent，确保 fastStream 读到完整的视觉数据。
     * 慢环不直接读 feelingBuffer（它读 trace 三缓冲），故不需要 visionEvent。
     */
    private volatile Event visionEvent;

    /**
     * dt 调试开关：开启时每轮输出快/慢环轮间时间间隔到日志。关闭时零性能损失。
     * <p>
     * dt 语义在 urana（nn 不知道 dt 含义），日志也由 urana 打印。
     */
    private boolean dtDebugEnabled = false;

    // === 工作线程（意识体的双节律运转）===

    /**
     * 工作线程是否应当继续运行。volatile 保证 awaken/shutdown 对两工作线程的停止信号可见。
     */
    private volatile boolean running = false;

    /**
     * close() 是否已执行过（防双释放）。
     * <p>
     * <b>save → shutdown 序列</b>（C3）：save 调 {@link #stopWorkersForSave} 置 running=false 并 join 线程，
     * 随后 shutdown 被调时 running 已 false——若无此标志，shutdown 的 {@code if (!running) return}
     * 会跳过 {@link #close()} 导致显存泄漏。故 shutdown 拆为"停线程（running 守卫）+ 释放（closed 守卫）"
     * 两段，close 只执行一次。
     */
    private boolean closed = false;

    // === 定期 save（C6 版本管理 + 崩溃恢复）===

    /**
     * 上次定期 save 的墙钟时间戳（毫秒）。0=尚未初始化（首次慢环轮懒初始化为 now，避免首启动立即触发）。
     * <p>
     * <b>单位是时间而非轮数</b>（真善美第3条）：定时持久化的"定时"是公共墙钟时间，不假设 AI 有"轮"概念。
     * 仅慢环工作线程读写，无需 volatile。
     */
    private long lastPeriodicSaveMs = 0;

    /**
     * 定期 save 间隔提供者（毫秒）。由外周（UranaProcessFactory）注入，读 per-maid 持久化配置。
     * 0=禁用定期 save。每次慢环轮检查，用户改 GUI 配置后立即生效（无需重 init）。
     * <p>
     * <b>不直接依赖 PersistenceConfigProvider</b>（真善美第3条）：本类不感知配置来源，只接收一个 LongSupplier。
     * 换配置来源时本类零改动。
     * <b>不感知"定时持久化开关"</b>（真善美第3条）：开关的开/关在 factory 层组合进此 provider
     * （关→返回 0L），本类只收"间隔(0=禁用)"。
     * 默认 () → 0L（禁用），awaken 前安全。
     */
    private LongSupplier periodicSaveIntervalProvider = () -> 0L;

    /**
     * 定期 save 槽位提供者。由外周注入，调 SaveSlotFactory.newVersion 创建新版本目录。
     * <p>
     * <b>不直接依赖 SaveSlotFactory</b>（真善美第3条）：本类不感知槽位创建细节，只接收一个 Supplier。
     * 默认 () → null（不 save），awaken 前安全。
     */
    private Supplier<SaveSlot> periodicSaveSlotProvider = () -> null;

    /**
     * 定期 save 后回调（版本清理）。由外周注入，调 SaveSlotFactory.pruneOldVersions。
     * <p>
     * <b>不直接依赖 SaveSlotFactory</b>（真善美第3条）：本类不感知清理策略，只接收一个 Runnable。
     * 默认 no-op。
     */
    private Runnable periodicPostSaveAction = () -> {};

    /**
     * 当前感觉缓冲区的引用（由外部 awaken 时注入）。
     * 快环每轮读取驱动流程二。引用在 awaken 后不再变化，volatile 明确跨线程可见语义。
     */
    private volatile VectorBase currentFeeling_original;

    /**
     * 快环最小轮间间隔（毫秒），由外部 awaken 时告知。
     * 0=不限速（全速运转）；>0=快环两轮间至少间隔该值，用 Thread.sleep 补足以降低 GPU 占用。
     * 启动时读一次，运行中固定。
     */
    private long fastMinDtMillis_original = 0;
    /**
     * 慢环最小轮间间隔（毫秒），由外部 awaken 时告知。
     * 0=不限速（全速运转）；>0=慢环两轮间至少间隔该值，用 Thread.sleep 补足以降低 GPU 占用。
     * 启动时读一次，运行中固定。
     */
    private long slowMinDtMillis_original = 0;

    /**
     * load 读入的"上一会话最后一轮快环开始时间"（T_save，墙钟毫秒）。
     * <p>
     * <b>休眠 dt</b>（C5）：首轮快环用 {@code T_wake − loadedLastTickStartMs} 作为 fastDt 喂给 urana，
     * 让 AI 跨会话"知道自己睡了多久"。0 表示无存档（首启动），首轮 fastDt 用 0。
     * <p>
     * 由 {@link #load(SaveSlot)} 写入（create 后、awaken 前），由 {@link #runFastLoop} 首轮读取。
     * 非线程可见性问题：load 在 awaken 前单线程调用，fastWorkerThread 在 awaken 后启动，
     * {@code awaken} 的 {@code start()} happens-before 工作线程读此值——无需 volatile。
     */
    private long loadedLastTickStartMs = 0;

    /**
     * 本会话最后一轮快环开始时间（T_save，墙钟毫秒），由快环工作线程每轮更新。
     * <p>
     * <b>save 用</b>（C5）：shutdown save 时写入 {@code last_tick_start_time.bin}，下次 load 读入
     * {@link #loadedLastTickStartMs}，构成跨会话时间连续性闭环。
     * <p>
     * volatile：快环工作线程写，save（主线程，join 快环线程后）读——join 的 happens-before
     * 已保证可见，但 volatile 明确语义。
     */
    private volatile long lastFastTickStartMs = 0;

    /**
     * 快环首轮标记：true 时首轮用休眠 dt（T_wake − loadedLastTickStartMs），首轮后置 false。
     * <p>
     * 非线程可见性：仅快环工作线程读写，单线程访问，无需 volatile。
     */
    private boolean fastFirstTick = true;

    /**
     * 快环工作线程（行动者，实时反应）。
     */
    private Thread fastWorkerThread;
    /**
     * 慢环工作线程（学习/反思，巩固）。
     */
    private Thread slowWorkerThread;

    private static final Logger LOGGER = LoggerFactory.getLogger("UranaSystem");

    // === 第一层：行动者 (Prospective) ===
    private final ProspectiveAnchor prospectiveAnchor_original; // 流程一（慢环）
    private final ProspectiveInference prospectiveInference_original; // 流程二（快环）
    private final ProspectiveGradCell prospectiveGradCell_original; // 流程三（慢环）

    // === 第二层：分析师 (Retrospective) ===
    private final RetrospectiveAnchor retrospectiveAnchor_original; // 流程五
    private final RetrospectiveInference retrospectiveInference_original; // 流程四
    private final RetrospectiveGradCell retrospectiveGradCell_original; // 流程六

    // === 第三层：反思者 (Introspective) ===
    private final IntrospectiveAnchor introspectiveAnchor_original; // 流程七 (数据容器)
    private final IntrospectiveInference introspectiveInference_original; // 流程七 (推理逻辑)
    private final TrainingContext introspectiveTrainingContext_original; // 流程八

    // === 跨时间步传递的状态 ===
    /**
     * 行动者工作记忆（流程二的继承信息 C）。快环独占维护，每轮由流程二读写。
     * 慢环不碰（流程三用 anchor 训练，不直接用 prospectiveInheritance_original）。
     */
    private VectorBase prospectiveInheritance_original;
    private VectorBase retrospectiveInheritance_original;
    private VectorBase introspectiveInheritance_original;

    /**
     * 痕迹快照三缓冲（快环写、慢环读）。
     * <p>
     * 快环每轮把 (流程二输入感觉 currentFeeling, 流程二输出行为 behavior) 成对写入
     * {@code traceFeeling_original[gen%3]} / {@code traceBehavior_original[gen%3]}，record {@code traceEvent[gen%3]}，
     * gen++。慢环每轮取最新一份 (gen-1)%3，waitEvent 后成对 push 进 anchor 的感觉/行为滑动窗口，
     * 保证 anchor 同一列 feeling/behavior 同时刻对齐（流程三训练 target 正确性所必需）。
     * <p>
     * 三缓冲而非双缓冲：快环频率约 5× 慢环，慢环读 traceBuffer 的窗口（两次 D2D push，~1ms）远小于
     * 快环写三轮的时间（~90ms），故慢环读槽 (gen-1)%3 时快环最多写到 (gen+1)%3，不触碰 (gen-1)%3，
     * 无撕裂。
     * <p>
     * 设计原则（真善美第 2 条）：复用 MappedGenerationBuffer 的"宣告产出 + 消费"同构模式——快环宣告
     * (感觉,行为)痕迹产出，慢环消费之填充记忆窗口。意识域 C 中"行动者留下痕迹、记忆从痕迹取样"
     * 的模式在此落地。
     */
    private final VectorBase[] traceFeeling_original;
    private final VectorBase[] traceBehavior_original;
    private final Event[] traceEvent;
    /**
     * 痕迹快照三缓冲配套的快环 dt（毫秒）。快环每轮留痕时把本轮 fastDt 写入对应槽位，
     * 慢环消费该槽位时把此 dt 传给流程三训练 prospective。
     * <p>
     * 设计原则（真善美第 2 条）：prospective 网络的时间认识应基于行动者自己的快节奏——
     * 训练样本来自快环痕迹，样本间真实间隔是 fastDt，故训练 dt 也应是 fastDt，与快环推理一致。
     * 三缓冲保证快慢环不同时读写同一槽位，long 读写无撕裂风险；traceGen_original 的 volatile
     * happens-before 保证慢环读 traceGen_original 后能看到快环在 traceGen_original++ 前写入的 fastDt 值。
     */
    private final long[] traceFastDt_original;
    /** 快环痕迹代际计数。volatile 供慢环读取最新值。 */
    private volatile int traceGen_original = 0;

    /**
     * 行为产出通道（意识-外周边界对象）。
     * <p>
     * 由外周（SmarterClientService）在 awaken 时注入，非 UranaSystem 所有。
     * <b>快环</b>只用其 producer 面：写入 buffer（extractBehaviorTo）+ publish 宣告产出。
     * 慢环不碰 MappedGenerationBuffer（behavior 只由快环产出）。
     */
    private MappedGenerationBuffer behaviorChannel_original;

    /**
     * 构造一个全新的 Urana 系统，注入 NN 实现、尺寸与双环节律参数。
     * <p>
     * 设计原则（真善美第3条）：把“可替换 NN”这个不实在约束实在化为构造注入——
     * UranaSystem 不依赖任何 BNN 实现类，外周（SmarterClientService）负责 new BnnNeuralNetwork 传入。
     * <p>
     * 节律参数（fastMinDtMillis/slowMinDtMillis）是 urana 双环特定的，故在构造函数接收而非
     * {@link #awaken} 签名里——IProcessSystem 接口的 awaken 只接通用依赖，不固化双环（真善美第2条：
     * C 中“有节律”是模式，“双环”不是）。
     *
     * @param nn               神经网络抽象边界（如 BnnNeuralNetwork）。
     * @param feelingSize      感觉向量尺寸（外周传入，UranaSystem 不反向依赖 nn 的尺寸）。
     * @param behaviorSize     行为向量尺寸。
     * @param fastMinDtMillis  快环最小轮间间隔（毫秒），0=不限速。
     * @param slowMinDtMillis  慢环最小轮间间隔（毫秒），0=不限速。
     */
    public UranaSystem(INeuralNetwork nn, InputVectorDomain inputDomain, OutputVectorDomain outputDomain, long fastMinDtMillis, long slowMinDtMillis) {
        this.nn_original = nn;
        this.inputDomain_original = inputDomain;
        this.outputDomain_original = outputDomain;
        this.fastMinDtMillis_original = fastMinDtMillis;
        this.slowMinDtMillis_original = slowMinDtMillis;

        // feelingSize/behaviorSize 从 domain 取（domain 持 urana 布局，长度项来自 nn profile）
        int feelingSize = inputDomain.getFeelingSpan().getLength();
        int behaviorSize = outputDomain.getBehaviorSpan().getLength();

        // --- 两环 CUDA 流（先于各 Cell 创建，供 runFastTick/runSlowTick 传入各 execute）---
        this.uranaStream = new Stream();   // 慢环
        this.fastStream = new Stream();    // 快环

        // --- 初始化所有组件（全部注入同一个 nn 抽象）---
        // 行动层
        this.prospectiveAnchor_original = new ProspectiveAnchor(nn, feelingSize, behaviorSize);
        this.prospectiveInference_original = new ProspectiveInference(nn);
        this.prospectiveGradCell_original = new ProspectiveGradCell(nn);

        // 分析层（outputDomain 注入供 pushFromOutput 取 span）
        this.retrospectiveAnchor_original = new RetrospectiveAnchor(nn, feelingSize, behaviorSize, outputDomain);
        this.retrospectiveInference_original = new RetrospectiveInference(nn);
        this.retrospectiveGradCell_original = new RetrospectiveGradCell(nn);

        // 反思层（outputDomain 注入供 pokeInto 取 span）
        this.introspectiveAnchor_original = new IntrospectiveAnchor(nn, feelingSize, behaviorSize, outputDomain);
        this.introspectiveInference_original = new IntrospectiveInference(nn);
        this.introspectiveTrainingContext_original = new TrainingContext(nn);

        // 初始化跨轮状态（避免首轮 NPE）—— 由 nn.createVector 创建，载体由 nn 决定
        int sizeC = inputDomain.getInheritanceInfoSpan().getLength();
        this.prospectiveInheritance_original = nn.createVector(sizeC);
        this.retrospectiveInheritance_original = nn.createVector(sizeC);
        this.introspectiveInheritance_original = nn.createVector(sizeC);

        // 痕迹快照三缓冲（快环产出 → 慢环消费）
        this.traceFeeling_original = new VectorBase[3];
        this.traceBehavior_original = new VectorBase[3];
        this.traceEvent = new Event[3];
        for (int i = 0; i < 3; i++) {
            this.traceFeeling_original[i] = nn.createVector(feelingSize);
            this.traceBehavior_original[i] = nn.createVector(behaviorSize);
            this.traceEvent[i] = new Event();
        }
        // 痕迹配套的快环 dt（long，每槽一个）——dt 值由 urana 持有（dt 语义在 urana）
        this.traceFastDt_original = new long[3];

        // behavior 产出通道（mapped buffer + generation counter）由外周 SmarterClientService
        // 创建并在 awaken 时注入，UranaSystem 不在此分配、不持有其生命周期。
    }

    /**
     * 将 Urana 系统核心状态序列化到磁盘（在 shutdown 释放显存前调用）。
     * <p>
     * <b>一致快照</b>（C3 安全）：先停止快/慢双工作线程并 join（保证无并发 GPU 读写），
     * 再做 D2H + 写文件。save 不释放显存——释放由后续 {@link #shutdown} 的 {@link #close()} 负责。
     * shutdown 调本方法后再调 {@link #shutdown()}，后者发现 {@link #running} 已 false、线程已 join，
     * 直接走 close() 释放显存（joinWorker 对已停止线程是 no-op）。
     * <p>
     * <b>每层自管持久化</b>（C2）：写 nn 层（{@code slot.layerPath("nn")}）+ urana 层
     * （{@code slot.layerPath("urana")}：∇C×3 + 继承×3 + 休眠时间）。nn 载体类型由 nn 自知
     * （nn.save/loadVector/loadGradientVector），urana 持 VectorBase 多态 save。
     * <p>
     * <b>休眠时间</b>（C5）：写 {@link #lastFastTickStartMs}（T_save），下次 load 读入
     * {@link #loadedLastTickStartMs}，首轮 fastDt = T_wake − T_save。
     *
     * @param slot 持久化槽位（指向新版本目录）
     */
    @Override
    public void save(SaveSlot slot) {
        if (slot == null) return;
        // 1. 停止双工作线程保证一致快照（join 后无并发 GPU 读写）
        stopWorkersForSave();
        // 2. D2H + 写文件
        saveToDisk(slot);
    }

    /**
     * D2H + 写文件的核心逻辑（save 与定期 snapshot 共用）。
     * <p>
     * <b>调用方须保证一致性</b>：
     * <ul>
     *   <li>shutdown save：{@link #save} 已调 {@link #stopWorkersForSave} join 双线程，无并发读写。</li>
     *   <li>定期 snapshot：慢环线程调用，慢环数据（∇C/继承/权重）本轮刚写完稳定；
     *       快环数据（prospectiveInheritance_original/lastFastTickStartMs）可能轻微陈旧——
     *       save 不接 stream 参数（内部同步），D2H 时短暂阻塞快环流，读到 old 或 new 不撕裂。
     *       对周期性 checkpoint 可接受（崩溃恢复用，非权威快照）。</li>
     * </ul>
     */
    private void saveToDisk(SaveSlot slot) {
        String nnPath = slot.layerPath("nn");
        String uranaPath = slot.layerPath("urana");
        new File(uranaPath).mkdirs();

        // NN 权重（nn.save 自管 b/p/q/l/r 文件）
        try {
            nn_original.save(nnPath);
        } catch (Exception e) {
            LOGGER.warn("[Urana] save nn 权重失败", e);
        }

        // ∇C 跨轮梯度缓冲 ×3（gradCell.save 用 nn 载体的 save，多态）
        try {
            prospectiveGradCell_original.save(uranaPath, "prospective");
            retrospectiveGradCell_original.save(uranaPath, "retrospective");
            introspectiveTrainingContext_original.save(uranaPath, "introspective");
        } catch (Exception e) {
            LOGGER.warn("[Urana] save ∇C 缓冲失败", e);
        }

        // 锚点全窗口记忆 ×3（anchor.save 多态，载体由 nn 决定）
        try {
            prospectiveAnchor_original.save(uranaPath, "prospective");
            retrospectiveAnchor_original.save(uranaPath, "retrospective");
            introspectiveAnchor_original.save(uranaPath, "introspective");
        } catch (Exception e) {
            LOGGER.warn("[Urana] save 锚点记忆失败", e);
        }

        // 继承信息 ×3（VectorBase.save 多态，载体由 nn 决定）
        saveInheritance(prospectiveInheritance_original, uranaPath, "prospective_inheritance_original.bin");
        saveInheritance(retrospectiveInheritance_original, uranaPath, "retrospective_inheritance_original.bin");
        saveInheritance(introspectiveInheritance_original, uranaPath, "introspective_inheritance_original.bin");

        // 休眠时间 T_save（C5）
        writeLong(new File(uranaPath, "last_tick_start_time_original.bin"), lastFastTickStartMs);
    }

    /**
     * 停止快/慢双工作线程并 join（供 {@link #save} 取一致快照）。
     * 不释放显存。已停止时是 no-op。
     */
    private void stopWorkersForSave() {
        if (!running) return; // 已停止（如 shutdown 已先调）
        running = false;
        joinWorker(fastWorkerThread, "UranaFastWorker");
        joinWorker(slowWorkerThread, "UranaSlowWorker");
    }

    /**
     * 序列化继承信息向量到磁盘（VectorBase.save 多态）。
     */
    private void saveInheritance(VectorBase target, String uranaPath, String fileName) {
        if (target == null) return;
        try {
            target.save(new File(uranaPath, fileName).getAbsolutePath());
        } catch (Exception e) {
            LOGGER.warn("[Urana] save 继承信息失败: {}", fileName, e);
        }
    }

    /**
     * 写一个 long 到文件（休眠时间 T_save）。
     */
    private void writeLong(File f, long value) {
        try {
            f.getParentFile().mkdirs();
            try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(f))) {
                dos.writeLong(value);
            }
        } catch (IOException e) {
            LOGGER.warn("[Urana] 写入休眠时间失败: {}", f, e);
        }
    }

    // ==================== 持久化（load：∇C/继承/休眠时间）====================

    /**
     * 从磁盘加载 urana 自身跨会话状态：∇C 跨轮梯度缓冲 ×3、继承信息 ×3、上一会话最后一轮快环开始时间。
     * <p>
     * <b>时机</b>（C3）：在 create 后、awaken 前调用——此时 fast/slow 工作线程尚未启动，
     * grad_C_buffer/inheritance 已由构造期创建（grad_C_buffer 已清零，inheritance 未初始化），
     * load 直接 D2D 覆盖写入，无并发风险。nn 权重由 {@code NnFactory.create} 在本方法之前已 load。
     * <p>
     * <b>每层自管持久化</b>（C2）：本方法只 load urana 层自身状态（{@code slot.layerPath("urana")}），
     * 不碰 nn 层（nn 已由 factory load）。nn 载体类型由 {@link INeuralNetwork#loadVector}/{@link INeuralNetwork#loadGradientVector}
     * 知晓——urana 持 {@link VectorBase} 引用能 save（多态）但 load 不知子类，故借 nn 对称 load 接口。
     * <p>
     * <b>优雅降级</b>：文件缺失（首启动/存档损坏）时各子项保持构造期默认（∇C=0、继承=未初始化、
     * 休眠时间=0），不抛异常。
     *
     * @param slot 持久化槽位（由 {@code UranaProcessFactory.create} 在构造本实例后传入）
     */
    public void load(SaveSlot slot) {
        if (slot == null) return; // 无槽位（理论不发生，防御）
        String uranaPath = slot.layerPath("urana");

        // ∇C 跨轮梯度缓冲 ×3（用 nn.loadGradientVector，nn 知载体类型）
        prospectiveGradCell_original.load(uranaPath, "prospective");
        retrospectiveGradCell_original.load(uranaPath, "retrospective");
        introspectiveTrainingContext_original.load(uranaPath, "introspective");

        // 锚点全窗口 ×3（用 nn.loadVector，数据向量载体由 nn 决定）
        prospectiveAnchor_original.load(uranaPath, "prospective");
        retrospectiveAnchor_original.load(uranaPath, "retrospective");
        introspectiveAnchor_original.load(uranaPath, "introspective");

        // 继承信息 ×3（用 nn.loadVector，数据向量载体由 nn 决定）
        loadInheritance(prospectiveInheritance_original, uranaPath, "prospective_inheritance_original.bin");
        loadInheritance(retrospectiveInheritance_original, uranaPath, "retrospective_inheritance_original.bin");
        loadInheritance(introspectiveInheritance_original, uranaPath, "introspective_inheritance_original.bin");

        // 休眠时间（C5）：T_save，供首轮 fastDt = T_wake − T_save
        loadedLastTickStartMs = readLong(new File(uranaPath, "last_tick_start_time_original.bin"));
    }

    /**
     * 加载继承信息向量到 target：文件存在则 nn.loadVector 造新实例 → D2D 拷入 target → 关闭临时实例。
     * 文件缺失保持 target 构造期状态。
     * <p>
     * NULL 流（0L）：load 在 awaken 前，无并发。
     */
    private void loadInheritance(VectorBase target, String uranaPath, String fileName) {
        File f = new File(uranaPath, fileName);
        if (!f.exists()) return;
        VectorBase loaded = nn_original.loadVector(f.getAbsolutePath());
        try {
            target.copyRegionFrom(loaded, fullSpan(loaded), fullSpan(target), 0L);
        } finally {
            try { loaded.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * 从文件读取一个 long（休眠时间 T_save）。文件缺失/异常返回 0（首启动/损坏 → 首轮 fastDt=0）。
     */
    private long readLong(File f) {
        if (!f.exists()) return 0;
        try (DataInputStream dis = new DataInputStream(new FileInputStream(f))) {
            return dis.readLong();
        } catch (IOException e) {
            LOGGER.warn("[Urana] 读取休眠时间失败: {}", f, e);
            return 0;
        }
    }

    /**
     * 注入定期 save 配置（由外周 UranaProcessFactory 在 create 后、awaken 前调用）。
     * <p>
     * <b>依赖注入而非直接依赖</b>（真善美第3条）：本类不感知 SaveSlotFactory/PersistenceConfigProvider，
     * 只接收三个函数式接口。换配置来源/槽位工厂/清理策略时本类零改动。
     * <p>
     * <b>默认值安全</b>：三参数默认值（interval=0/slot=null/no-op）使 awaken 前的慢环轮（理论不发生，
     * awaken 才启动线程）即使触发也是 no-op。
     *
     * @param slotProvider    定期 save 时创建新版本槽位（调 SaveSlotFactory.newVersion）
     * @param intervalProvider save 间隔（毫秒，0=禁用），每次慢环轮动态查询。factory 层组合定时开关（关→0L）。
     * @param postSaveAction  save 后回调（版本清理，调 SaveSlotFactory.pruneOldVersions）
     */
    public void setPeriodicSaveConfig(Supplier<SaveSlot> slotProvider,
                                       LongSupplier intervalProvider,
                                       Runnable postSaveAction) {
        this.periodicSaveSlotProvider = slotProvider != null ? slotProvider : () -> null;
        this.periodicSaveIntervalProvider = intervalProvider != null ? intervalProvider : () -> 0L;
        this.periodicPostSaveAction = postSaveAction != null ? postSaveAction : () -> {};
    }

    // ==================== 快环（行动者，实时反应）====================

    /**
     * 快环单轮：行动者推理，产出 behavior 并宣告，同时留痕供慢环消费。
     * <p>
     * 流程二直接吃 {@code currentFeeling}（不经流程一 anchor 中转——流程一已移到慢环），
     * 在 {@link #fastStream} 上执行前向推理。产出 behavior 写入 MappedGenerationBuffer 并 publish
     * 宣告给效应器；行动者工作记忆 prospectiveInheritance_original 本轮更新。
     * 同时把 (currentFeeling, behavior) 成对快照到 trace 三缓冲，record traceEvent 供慢环消费。
     *
     * @param currentFeeling 从环境中感知的当前感觉（视觉采集写入的 GPU 缓冲）。
     * @param dtMillis       快环本轮与上一轮之间的墙钟时间间隔（毫秒）。首轮为 0。
     */
    private void runFastTick(VectorBase currentFeeling, long dtMillis) {
        // vision→快环 显式同步：等视觉流把最新 feelingBuffer 写完，再开始本轮推理。
        if (visionEvent != null) {
            fastStream.waitEvent(visionEvent);
        }
        long stream = this.fastStream.getHandle();

        // dt 日志在 urana 打（dt 语义在 urana，nn 不知道 dt 含义）
        if (dtDebugEnabled) {
            LOGGER.info("[UranaFast] dt={} ms", dtMillis);
        }

        // [流程二] N=2 链条：直接吃 currentFeeling（不再经 prospectiveAnchor_original 中转），
        // 用上轮 prospectiveInheritance_original 作 C 输入。dt 传给适配器，由适配器调 nn.copyToInputFromLong 编码。
        VectorBase prospectiveOutput = prospectiveInference_original.execute(
                currentFeeling, this.prospectiveInheritance_original, dtMillis, stream);

        // behavior → 外周通道（效应器消费）。从推理结果取 behavior 区段拷到 behaviorChannel_original buffer。
        // 模式：urana 自己的向量间搬运，用 VectorBase.copyRegionFrom。
        int behaviorLen = this.outputDomain_original.getBehaviorSpan().getLength();
        this.behaviorChannel_original.getBuffer().copyRegionFrom(
                prospectiveOutput,
                this.outputDomain_original.getBehaviorSpan(),
                new BehaviorSpan(0, behaviorLen),
                stream);
        // 行动者工作记忆更新（快环独占）：取 C 区段。
        int cLen = this.outputDomain_original.getInheritanceInfoSpan().getLength();
        this.prospectiveInheritance_original.copyRegionFrom(
                prospectiveOutput,
                this.outputDomain_original.getInheritanceInfoSpan(),
                new Span(0, cLen) {},
                stream);

        // 宣告行为产出完成（供效应器消费）。流内有序：排在写 buffer 之后。
        this.behaviorChannel_original.publish(stream);

        // 留痕：把 (本轮输入感觉, 本轮输出行为, 本轮 fastDt) 三元组快照到 trace 三缓冲，供慢环消费。
        // 成对取自同一快环轮 → 保证慢环 anchor 同一列 feeling/behavior 同时刻对齐；
        // fastDt 一并存 → 慢环流程三训练 prospective 时用此 dt，与快环推理 dt 一致（真善美第 2 条：
        // prospective 时间认识基于行动者快节奏，训练样本的真实间隔是 fastDt）。
        int idx = this.traceGen_original % 3;
        this.traceFeeling_original[idx].copyRegionFrom(
                currentFeeling, fullSpan(currentFeeling), fullSpan(this.traceFeeling_original[idx]), stream);
        this.traceBehavior_original[idx].copyRegionFrom(
                prospectiveOutput, this.outputDomain_original.getBehaviorSpan(),
                new BehaviorSpan(0, behaviorLen), stream);
        this.traceFastDt_original[idx] = dtMillis;
        this.traceEvent[idx].record(stream);
        this.traceGen_original++;
    }

    // ==================== 慢环（学习/反思，巩固）====================

    /**
     * 慢环单轮：流程一/三/四/五/六/七/八按算法.md 编号顺序依次执行（流程二在快环）。
     * <p>
     * 流程一从 trace 三缓冲取最新一份成对 (feeling, behavior) push 进 prospectiveAnchor_original，保证同一列
     * 同时刻对齐。随后流程三（行动层学习）、流程四/五（分析层推理+锚点）、流程六（分析层学习）、
     * 流程七/八（反思层推理+训练）依次在 {@link #uranaStream} 上执行。学习/反思按慢环自身节律连续积累。
     *
     * @param dtMillis 慢环本轮与上一轮之间的墙钟时间间隔（毫秒）。首轮为 0。
     */
    private void runSlowTick(long dtMillis) {
        long stream = this.uranaStream.getHandle();

        // dt 日志在 urana 打
        if (dtDebugEnabled) {
            LOGGER.info("[UranaSlow] dt={} ms", dtMillis);
        }

        // [流程一] 从快环最新痕迹快照成对 push 进 prospectiveAnchor_original。
        this.prospectiveAnchor_original.tick();
        int latestGen = this.traceGen_original - 1;
        if (latestGen < 0) {
            // 快环尚未产出任何痕迹：跳过本轮 anchor 填充，后续流程也无可训数据，直接返回等下一轮。
            return;
        }
        int tIdx = ((latestGen % 3) + 3) % 3;
        this.uranaStream.waitEvent(this.traceEvent[tIdx]);
        // 如果运行的时机不当，以下两行代码可能向prospectiveAnchor_original压入不同时刻的感觉向量和行为向量。
        // 不过现在的算力情况下，这种情况及其罕见，系统一定程度上可以容错。
        this.prospectiveAnchor_original.pushFeelingFrom(this.traceFeeling_original[tIdx], stream);
        this.prospectiveAnchor_original.pushBehaviorFrom(this.traceBehavior_original[tIdx], stream);

        // [流程三] 行动层梯度下降：读 prospectiveAnchor_original（suspension+precipitate）训练，写权重。
        // dt 用 trace 中存的 fastDt（不是 slowDt）：prospective 网络的时间认识基于行动者快节奏，
        // 训练样本来自快环痕迹、样本间真实间隔是 fastDt，故训练 dt 也用 fastDt，与快环推理一致。
        // 权重与快环 forward 并发读写，安全靠 32 位原子性（方案 C，见类注释）。
        long fastDtForTraining = this.traceFastDt_original[tIdx];
        if (dtDebugEnabled) {
            LOGGER.info("[UranaProTrain] dt={} ms", fastDtForTraining);
        }
        prospectiveGradCell_original.execute(prospectiveAnchor_original, fastDtForTraining, stream);

        // [流程四] N=1：F=锚点悬浮物（上一轮输出F），C=上轮输出C（分析层自身状态，不碰 prospectiveAnchor_original）
        // 真善美第2条：消除 lastRetrospectiveFeeling 冗余，锚点成为“上一轮输出F”唯一真源。
        // 安全：流程四在流程五 tick() 之前执行，此时 suspension 仍是上一轮输出（未被本轮滑动），
        // 流程四只 copyToInput 读 F、不改 F，读锚点 suspension 不破坏锚点；流程四读完流程五才 tick+push。
        VectorBase retrospectiveOutput = retrospectiveInference_original.execute(
                retrospectiveAnchor_original.getFeeling().getSuspension(), this.retrospectiveInheritance_original, dtMillis, stream);
        // [流程五] retrospectiveAnchor_original 滑动 + 填充
        retrospectiveAnchor_original.tick();
        retrospectiveAnchor_original.pushFromOutput(retrospectiveOutput, stream);
        // 取 C 给下一轮：用 VectorBase.copyRegionFrom
        int cLen = this.outputDomain_original.getInheritanceInfoSpan().getLength();
        this.retrospectiveInheritance_original.copyRegionFrom(
                retrospectiveOutput, this.outputDomain_original.getInheritanceInfoSpan(),
                new Span(0, cLen) {}, stream);

        // [流程六] 分析层梯度下降：写权重。
        retrospectiveGradCell_original.execute(retrospectiveAnchor_original, dtMillis, stream);

        // [流程七] N=1：F=锚点沉淀物，C=上轮inheritance
        introspectiveAnchor_original.pokeFrom(retrospectiveAnchor_original, stream);
        VectorBase introspectiveOutput = introspectiveInference_original.execute(
                introspectiveAnchor_original.getFeeling().getPrecipitate(),
                this.introspectiveInheritance_original,
                dtMillis,
                stream);
        introspectiveAnchor_original.pokeInto(introspectiveOutput, introspectiveOutput, stream);
        this.introspectiveInheritance_original.copyRegionFrom(
                introspectiveOutput, this.outputDomain_original.getInheritanceInfoSpan(),
                new Span(0, cLen) {}, stream);

        // [流程八] 反思层训练：写权重。
        introspectiveTrainingContext_original.execute(introspectiveAnchor_original, dtMillis, stream);

        // [定期 save]（C6 崩溃恢复）：每隔 N 毫秒（墙钟时间）做一次 snapshot save。
        // 慢环线程同步执行（慢环不追求实时，save 期间慢环阻塞可接受）；
        // saveToDisk 的 D2H 短暂阻塞快环流（save 不接 stream 参数，内部同步），读 old 或 new 不撕裂。
        // 单位是时间而非轮数（真善美第3条）：定时持久化的"定时"是公共墙钟时间，不假设 AI 有"轮"概念。
        long now = System.currentTimeMillis();
        if (lastPeriodicSaveMs == 0) {
            lastPeriodicSaveMs = now; // 首次懒初始化，避免首启动立即触发
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
                LOGGER.warn("[Urana] 定期 snapshot save 失败", e);
            }
        }
    }

    /**
     * 唤醒 Urana 意识体，启动快慢双工作线程持续运转。
     * <p>
     * 快环（行动者）高频产出 behavior；慢环（学习/反思）按自身节律巩固。两环并发于双 CUDA 流，
     * 权重共享并发安全靠方案 C（见类注释）。
     * <p>
     * 双环节律参数由构造函数接收（不在本签名里），符合 IProcessSystem 通用契约——
     * 单环流程系统实现 IProcessSystem 时 awaken 签名相同，只是不需要 dt 参数。
     *
     * @param feelingBuffer  外部持有的感觉缓冲区，由视觉采集写入，快环每轮读取。
     * @param visionEvent    视觉采集完成事件，由外部创建并在视觉采集后 record。快环 waitEvent。非 Urana 所有。
     * @param behaviorChannel 行为产出通道，由外周创建注入。快环只用其 producer 面。非 Urana 所有。
     */
    @Override
    public void awaken(VectorBase feelingBuffer, Event visionEvent, MappedGenerationBuffer behaviorChannel) {
        if (running) return;
        this.currentFeeling_original = feelingBuffer;
        this.visionEvent = visionEvent;
        this.behaviorChannel_original = behaviorChannel;
        this.running = true;
        this.fastWorkerThread = new Thread(this::runFastLoop, "UranaFastWorker");
        this.slowWorkerThread = new Thread(this::runSlowLoop, "UranaSlowWorker");
        this.fastWorkerThread.setDaemon(true);
        this.slowWorkerThread.setDaemon(true);
        this.fastWorkerThread.start();
        this.slowWorkerThread.start();
    }

    /**
     * 关闭 Urana 意识体，停止快慢双工作线程并释放所有 GPU 资源。
     * 最多各等待 1.5 秒让两线程自然结束本轮，超时则中断后最多再等 0.5 秒。
     * <p>
     * <b>save → shutdown 序列</b>（C3）：若 {@link #save} 已先调（running 已 false、线程已 join），
     * 本方法跳过停线程段但仍执行 {@link #close()} 释放显存——{@link #closed} 标志防双释放。
     */
    @Override
    public void shutdown() {
        if (running) {
            running = false;
            joinWorker(fastWorkerThread, "UranaFastWorker");
            joinWorker(slowWorkerThread, "UranaSlowWorker");
            fastWorkerThread = null;
            slowWorkerThread = null;
        }
        if (closed) return;
        closed = true;
        try {
            close();
        } catch (Exception e) {
            LOGGER.error("[Urana] 资源释放异常", e);
        }
    }

    private void joinWorker(Thread t, String name) {
        if (t == null) return;
        try {
            t.join(1500);
            if (t.isAlive()) {
                t.interrupt();
                t.join(500);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 快环主循环：持续驱动 {@link #runFastTick}。
     * <p>
     * <b>dt 计算</b>：
     * <ul>
     *   <li>首轮（{@code fastFirstTick}）：用<b>休眠 dt</b> = {@code T_wake − loadedLastTickStartMs}
     *       （T_wake = 本轮墙钟开始时间）。让 AI 跨会话"知道自己睡了多久"（C5）。
     *       无存档（loadedLastTickStartMs=0）时首轮 dt=0（与旧逻辑一致）。</li>
     *   <li>非首轮：用 nanoTime 差值算 fastDt（同会话内单调、高精度）。</li>
     * </ul>
     * <b>为何首轮用墙钟、非首轮用 nanoTime</b>：休眠 dt 跨会话，须用墙钟 currentTimeMillis
     * （nanoTime 不跨 JVM 实例可比）；同会话内 dt 用 nanoTime 避免墙钟回拨、高精度。
     * <p>
     * 每轮记录 {@link #lastFastTickStartMs}（墙钟）供 shutdown save 写 T_save。
     * 受 fastMinDtMillis 节流。
     */
    private void runFastLoop() {
        long lastRunStartNanos = 0;
        while (running && !Thread.currentThread().isInterrupted()) {
            long now = System.nanoTime();
            long tickStartMs = System.currentTimeMillis();
            long dtMillis;
            if (fastFirstTick) {
                // 首轮：休眠 dt = T_wake − T_save（无存档则 0）
                dtMillis = (loadedLastTickStartMs > 0) ? Math.max(0, tickStartMs - loadedLastTickStartMs) : 0;
                fastFirstTick = false;
            } else {
                dtMillis = (lastRunStartNanos == 0) ? 0 : (now - lastRunStartNanos) / 1_000_000;
            }
            lastRunStartNanos = now;
            lastFastTickStartMs = tickStartMs; // 记录 T_save 供 shutdown save

            try {
                runFastTick(currentFeeling_original, dtMillis);
            } catch (Exception e) {
                LOGGER.error("[Urana][Fast] 工作线程 runFastTick 异常", e);
            }

            throttle(fastMinDtMillis_original, lastRunStartNanos);
        }
    }

    /**
     * 慢环主循环：持续驱动 {@link #runSlowTick}。
     * dt 由慢环自行用 nanoTime 差值计算。首轮 dt=0。受 slowMinDtMillis 节流（通常慢环一轮已 > slowMinDtMillis，
     * 节流为 no-op；仅 slowMinDtMillis 较大时补足）。
     */
    private void runSlowLoop() {
        long lastRunStartNanos = 0;
        while (running && !Thread.currentThread().isInterrupted()) {
            long now = System.nanoTime();
            long dtMillis = (lastRunStartNanos == 0) ? 0 : (now - lastRunStartNanos) / 1_000_000;
            lastRunStartNanos = now;

            try {
                runSlowTick(dtMillis);
            } catch (Exception e) {
                LOGGER.error("[Urana][Slow] 工作线程 runSlowTick 异常", e);
            }

            throttle(slowMinDtMillis_original, lastRunStartNanos);
        }
    }

    /**
     * 节流：保证轮间间隔不小于 minDtMillis（真善美第3条：用 Thread.sleep 把
     * “最小间隔”这个不实在的节律实在化为工作线程暂停），降低 GPU 占用。
     * 快/慢环各自传入自己的 minDtMillis，互不影响。
     */
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

    /**
     * dt 调试开关：开启时每轮输出快/慢环轮间时间间隔到日志。关闭时零性能损失。
     */
    @Override
    public void setDtDebugEnabled(boolean enabled) {
        this.dtDebugEnabled = enabled;
    }

    @Override
    public int feelingSize() {
        return inputDomain_original.getFeelingSpan().getLength();
    }

    @Override
    public int behaviorSize() {
        return outputDomain_original.getBehaviorSpan().getLength();
    }

    private Span fullSpan(VectorBase vector) {
        return new Span(0, vector.size()) {
        };
    }

    @Override
    public void close() throws Exception {
        // 在此释放所有 AutoCloseable 资源
        prospectiveAnchor_original.close();
        prospectiveInference_original.close();
        prospectiveGradCell_original.close();
        retrospectiveAnchor_original.close();
        retrospectiveInference_original.close();
        retrospectiveGradCell_original.close();
        introspectiveAnchor_original.close();
        introspectiveInference_original.close();
        introspectiveTrainingContext_original.close();

        // ✅ 释放共享网络（nn 实现自管其内部资源：权重/IO/梯度/target）
        if (nn_original != null)
            nn_original.close();

        // 补充释放跨轮状态
        if (prospectiveInheritance_original != null)
            prospectiveInheritance_original.close();
        if (retrospectiveInheritance_original != null)
            retrospectiveInheritance_original.close();
        if (introspectiveInheritance_original != null)
            introspectiveInheritance_original.close();

        // 释放痕迹快照三缓冲
        if (traceFeeling_original != null) {
            for (VectorBase v : traceFeeling_original) {
                if (v != null) v.close();
            }
        }
        if (traceBehavior_original != null) {
            for (VectorBase v : traceBehavior_original) {
                if (v != null) v.close();
            }
        }
        if (traceEvent != null) {
            for (Event e : traceEvent) {
                if (e != null) {
                    try { e.close(); } catch (Exception ignored) {}
                }
            }
        }

        // ✅ 释放两环 CUDA 流（两工作线程已 shutdown join，无并发访问）
        if (uranaStream != null) {
            try { uranaStream.close(); } catch (Exception ignored) {}
        }
        if (fastStream != null) {
            try { fastStream.close(); } catch (Exception ignored) {}
        }

        // behavior 产出通道（MappedGenerationBuffer）非本类所有（由外周注入），不在本处关闭；
        // 其生命周期由 SmarterClientService 在工作线程 join 后负责。
    }
}
