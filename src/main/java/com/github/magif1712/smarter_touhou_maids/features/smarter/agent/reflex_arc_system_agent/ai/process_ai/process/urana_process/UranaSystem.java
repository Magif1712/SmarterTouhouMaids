package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.IProcessSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.InputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.OutputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.subspan.BehaviorSpan;
import com.github.magif1712.smarter_touhou_maids.core.execution.MappedGenerationBuffer;
import com.github.magif1712.smarter_touhou_maids.core.execution.event.Event;
import com.github.magif1712.smarter_touhou_maids.core.execution.stream.Stream;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.subsystem.introspective.IntrospectiveAnchor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.subsystem.introspective.context.IntrospectiveInference;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.subsystem.introspective.context.TrainingContext;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.subsystem.prospective.ProspectiveAnchor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.subsystem.prospective.context.ProspectiveGradCell;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.subsystem.prospective.context.ProspectiveInference;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.subsystem.retrospective.RetrospectiveAnchor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.subsystem.retrospective.context.RetrospectiveGradCell;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.subsystem.retrospective.context.RetrospectiveInference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * 并把 (输入感觉, 输出行为, fastDt) 三元组快照到 trace 三缓冲供慢环消费。prospectiveInheritance（行动者
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

    private static final OutputVectorDomain OUTPUT_DOMAIN = new OutputVectorDomain();

    // === 尺寸（构造函数注入，供 feelingSize()/behaviorSize() 返回外周）===
    private final int feelingSize;
    private final int behaviorSize;

    // === 共享网络（抽象接口，可替换实现）===
    private final INeuralNetwork nn;

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
     * 当前感觉缓冲区的引用（由外部 awaken 时注入）。
     * 快环每轮读取驱动流程二。引用在 awaken 后不再变化，volatile 明确跨线程可见语义。
     */
    private volatile VectorBase currentFeeling;

    /**
     * 快环最小轮间间隔（毫秒），由外部 awaken 时告知。
     * 0=不限速（全速运转）；>0=快环两轮间至少间隔该值，用 Thread.sleep 补足以降低 GPU 占用。
     * 启动时读一次，运行中固定。
     */
    private long fastMinDtMillis = 0;
    /**
     * 慢环最小轮间间隔（毫秒），由外部 awaken 时告知。
     * 0=不限速（全速运转）；>0=慢环两轮间至少间隔该值，用 Thread.sleep 补足以降低 GPU 占用。
     * 启动时读一次，运行中固定。
     */
    private long slowMinDtMillis = 0;

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
    private final ProspectiveAnchor prospectiveAnchor; // 流程一（慢环）
    private final ProspectiveInference prospectiveInference; // 流程二（快环）
    private final ProspectiveGradCell prospectiveGradCell; // 流程三（慢环）

    // === 第二层：分析师 (Retrospective) ===
    private final RetrospectiveAnchor retrospectiveAnchor; // 流程五
    private final RetrospectiveInference retrospectiveInference; // 流程四
    private final RetrospectiveGradCell retrospectiveGradCell; // 流程六

    // === 第三层：反思者 (Introspective) ===
    private final IntrospectiveAnchor introspectiveAnchor; // 流程七 (数据容器)
    private final IntrospectiveInference introspectiveInference; // 流程七 (推理逻辑)
    private final TrainingContext introspectiveTrainingContext; // 流程八

    // === 跨时间步传递的状态 ===
    /**
     * 行动者工作记忆（流程二的继承信息 C）。快环独占维护，每轮由流程二读写。
     * 慢环不碰（流程三用 anchor 训练，不直接用 prospectiveInheritance）。
     */
    private VectorBase prospectiveInheritance;
    private VectorBase retrospectiveInheritance;
    private VectorBase introspectiveInheritance;
    private VectorBase lastRetrospectiveFeeling;

    /**
     * 痕迹快照三缓冲（快环写、慢环读）。
     * <p>
     * 快环每轮把 (流程二输入感觉 currentFeeling, 流程二输出行为 behavior) 成对写入
     * {@code traceFeeling[gen%3]} / {@code traceBehavior[gen%3]}，record {@code traceEvent[gen%3]}，
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
    private final VectorBase[] traceFeeling;
    private final VectorBase[] traceBehavior;
    private final Event[] traceEvent;
    /**
     * 痕迹快照三缓冲配套的快环 dt（毫秒）。快环每轮留痕时把本轮 fastDt 写入对应槽位，
     * 慢环消费该槽位时把此 dt 传给流程三训练 prospective。
     * <p>
     * 设计原则（真善美第 2 条）：prospective 网络的时间认识应基于行动者自己的快节奏——
     * 训练样本来自快环痕迹，样本间真实间隔是 fastDt，故训练 dt 也应是 fastDt，与快环推理一致。
     * 三缓冲保证快慢环不同时读写同一槽位，long 读写无撕裂风险；traceGen 的 volatile
     * happens-before 保证慢环读 traceGen 后能看到快环在 traceGen++ 前写入的 fastDt 值。
     */
    private final long[] traceFastDt;
    /** 快环痕迹代际计数。volatile 供慢环读取最新值。 */
    private volatile int traceGen = 0;

    /**
     * 行为产出通道（意识-外周边界对象）。
     * <p>
     * 由外周（SmarterClientService）在 awaken 时注入，非 UranaSystem 所有。
     * <b>快环</b>只用其 producer 面：写入 buffer（extractBehaviorTo）+ publish 宣告产出。
     * 慢环不碰 MappedGenerationBuffer（behavior 只由快环产出）。
     */
    private MappedGenerationBuffer behaviorChannel;

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
    public UranaSystem(INeuralNetwork nn, int feelingSize, int behaviorSize, long fastMinDtMillis, long slowMinDtMillis) {
        this.nn = nn;
        this.feelingSize = feelingSize;
        this.behaviorSize = behaviorSize;
        this.fastMinDtMillis = fastMinDtMillis;
        this.slowMinDtMillis = slowMinDtMillis;

        // --- 两环 CUDA 流（先于各 Cell 创建，供 runFastTick/runSlowTick 传入各 execute）---
        this.uranaStream = new Stream();   // 慢环
        this.fastStream = new Stream();    // 快环

        // --- 初始化所有组件（全部注入同一个 nn 抽象）---
        // 行动层
        this.prospectiveAnchor = new ProspectiveAnchor(nn, feelingSize, behaviorSize);
        this.prospectiveInference = new ProspectiveInference(nn);
        this.prospectiveGradCell = new ProspectiveGradCell(nn);

        // 分析层
        this.retrospectiveAnchor = new RetrospectiveAnchor(nn, feelingSize, behaviorSize);
        this.retrospectiveInference = new RetrospectiveInference(nn);
        this.retrospectiveGradCell = new RetrospectiveGradCell(nn);

        // 反思层
        this.introspectiveAnchor = new IntrospectiveAnchor(nn, feelingSize, behaviorSize);
        this.introspectiveInference = new IntrospectiveInference(nn);
        this.introspectiveTrainingContext = new TrainingContext(nn);

        // 初始化跨轮状态（避免首轮 NPE）—— 由 nn.createVector 创建，载体由 nn 决定
        int sizeC = new InputVectorDomain()
                .getInheritanceInfoSpan().getLength();
        this.prospectiveInheritance = nn.createVector(sizeC);
        this.retrospectiveInheritance = nn.createVector(sizeC);
        this.introspectiveInheritance = nn.createVector(sizeC);
        this.lastRetrospectiveFeeling = nn.createVector(feelingSize);

        // 痕迹快照三缓冲（快环产出 → 慢环消费）
        this.traceFeeling = new VectorBase[3];
        this.traceBehavior = new VectorBase[3];
        this.traceEvent = new Event[3];
        for (int i = 0; i < 3; i++) {
            this.traceFeeling[i] = nn.createVector(feelingSize);
            this.traceBehavior[i] = nn.createVector(behaviorSize);
            this.traceEvent[i] = new Event();
        }
        // 痕迹配套的快环 dt（long，每槽一个）——dt 值由 urana 持有（dt 语义在 urana）
        this.traceFastDt = new long[3];

        // behavior 产出通道（mapped buffer + generation counter）由外周 SmarterClientService
        // 创建并在 awaken 时注入，UranaSystem 不在此分配、不持有其生命周期。
    }

    /**
     * 将 Urana 系统的核心网络序列化到磁盘。
     *
     * @param folderPath 目标文件夹路径。
     */
    @Override
    public void save(String folderPath) {
        nn.save(folderPath);
    }

    // ==================== 快环（行动者，实时反应）====================

    /**
     * 快环单轮：行动者推理，产出 behavior 并宣告，同时留痕供慢环消费。
     * <p>
     * 流程二直接吃 {@code currentFeeling}（不经流程一 anchor 中转——流程一已移到慢环），
     * 在 {@link #fastStream} 上执行前向推理。产出 behavior 写入 MappedGenerationBuffer 并 publish
     * 宣告给效应器；行动者工作记忆 prospectiveInheritance 本轮更新。
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

        // [流程二] N=2 链条：直接吃 currentFeeling（不再经 prospectiveAnchor 中转），
        // 用上轮 prospectiveInheritance 作 C 输入。dt 传给适配器，由适配器调 nn.copyToInputFromLong 编码。
        VectorBase prospectiveOutput = prospectiveInference.execute(
                currentFeeling, this.prospectiveInheritance, dtMillis, stream);

        // behavior → 外周通道（效应器消费）。从推理结果取 behavior 区段拷到 behaviorChannel buffer。
        // 模式：urana 自己的向量间搬运，用 VectorBase.copyRegionFrom。
        int behaviorLen = OUTPUT_DOMAIN.getBehaviorSpan().getLength();
        this.behaviorChannel.getBuffer().copyRegionFrom(
                prospectiveOutput,
                OUTPUT_DOMAIN.getBehaviorSpan(),
                new BehaviorSpan(0, behaviorLen),
                stream);
        // 行动者工作记忆更新（快环独占）：取 C 区段。
        int cLen = OUTPUT_DOMAIN.getInheritanceInfoSpan().getLength();
        this.prospectiveInheritance.copyRegionFrom(
                prospectiveOutput,
                OUTPUT_DOMAIN.getInheritanceInfoSpan(),
                new Span(0, cLen) {},
                stream);

        // 宣告行为产出完成（供效应器消费）。流内有序：排在写 buffer 之后。
        this.behaviorChannel.publish(stream);

        // 留痕：把 (本轮输入感觉, 本轮输出行为, 本轮 fastDt) 三元组快照到 trace 三缓冲，供慢环消费。
        // 成对取自同一快环轮 → 保证慢环 anchor 同一列 feeling/behavior 同时刻对齐；
        // fastDt 一并存 → 慢环流程三训练 prospective 时用此 dt，与快环推理 dt 一致（真善美第 2 条：
        // prospective 时间认识基于行动者快节奏，训练样本的真实间隔是 fastDt）。
        int idx = this.traceGen % 3;
        this.traceFeeling[idx].copyRegionFrom(
                currentFeeling, fullSpan(currentFeeling), fullSpan(this.traceFeeling[idx]), stream);
        this.traceBehavior[idx].copyRegionFrom(
                prospectiveOutput, OUTPUT_DOMAIN.getBehaviorSpan(),
                new BehaviorSpan(0, behaviorLen), stream);
        this.traceFastDt[idx] = dtMillis;
        this.traceEvent[idx].record(stream);
        this.traceGen++;
    }

    // ==================== 慢环（学习/反思，巩固）====================

    /**
     * 慢环单轮：流程一/三/四/五/六/七/八按算法.md 编号顺序依次执行（流程二在快环）。
     * <p>
     * 流程一从 trace 三缓冲取最新一份成对 (feeling, behavior) push 进 prospectiveAnchor，保证同一列
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

        // [流程一] 从快环最新痕迹快照成对 push 进 prospectiveAnchor。
        this.prospectiveAnchor.tick();
        int latestGen = this.traceGen - 1;
        if (latestGen < 0) {
            // 快环尚未产出任何痕迹：跳过本轮 anchor 填充，后续流程也无可训数据，直接返回等下一轮。
            return;
        }
        int tIdx = ((latestGen % 3) + 3) % 3;
        this.uranaStream.waitEvent(this.traceEvent[tIdx]);
        // 如果运行的时机不当，以下两行代码可能向prospectiveAnchor压入不同时刻的感觉向量和行为向量。
        // 不过现在的算力情况下，这种情况及其罕见，系统一定程度上可以容错。
        this.prospectiveAnchor.pushFeelingFrom(this.traceFeeling[tIdx], stream);
        this.prospectiveAnchor.pushBehaviorFrom(this.traceBehavior[tIdx], stream);

        // [流程三] 行动层梯度下降：读 prospectiveAnchor（suspension+precipitate）训练，写权重。
        // dt 用 trace 中存的 fastDt（不是 slowDt）：prospective 网络的时间认识基于行动者快节奏，
        // 训练样本来自快环痕迹、样本间真实间隔是 fastDt，故训练 dt 也用 fastDt，与快环推理一致。
        // 权重与快环 forward 并发读写，安全靠 32 位原子性（方案 C，见类注释）。
        long fastDtForTraining = this.traceFastDt[tIdx];
        if (dtDebugEnabled) {
            LOGGER.info("[UranaProTrain] dt={} ms", fastDtForTraining);
        }
        prospectiveGradCell.execute(prospectiveAnchor, fastDtForTraining, stream);

        // [流程四] N=1：F=上轮输出F，C=上轮输出C（分析层自身状态，不碰 prospectiveAnchor）
        VectorBase retrospectiveOutput = retrospectiveInference.execute(
                this.lastRetrospectiveFeeling, this.retrospectiveInheritance, dtMillis, stream);
        // [流程五] retrospectiveAnchor 滑动 + 填充
        retrospectiveAnchor.tick();
        retrospectiveAnchor.pushFromOutput(retrospectiveOutput, stream);
        // 取 C 和 F 给下一轮：用 VectorBase.copyRegionFrom
        int cLen = OUTPUT_DOMAIN.getInheritanceInfoSpan().getLength();
        int fLen = OUTPUT_DOMAIN.getFeelingSpan().getLength();
        this.retrospectiveInheritance.copyRegionFrom(
                retrospectiveOutput, OUTPUT_DOMAIN.getInheritanceInfoSpan(),
                new Span(0, cLen) {}, stream);
        this.lastRetrospectiveFeeling.copyRegionFrom(
                retrospectiveOutput, OUTPUT_DOMAIN.getFeelingSpan(),
                new Span(0, fLen) {}, stream);

        // [流程六] 分析层梯度下降：写权重。
        retrospectiveGradCell.execute(retrospectiveAnchor, dtMillis, stream);

        // [流程七] N=1：F=锚点沉淀物，C=上轮inheritance
        introspectiveAnchor.pokeFrom(retrospectiveAnchor, stream);
        VectorBase introspectiveOutput = introspectiveInference.execute(
                introspectiveAnchor.getFeeling().getPrecipitate(),
                this.introspectiveInheritance,
                dtMillis,
                stream);
        introspectiveAnchor.pokeInto(introspectiveOutput, introspectiveOutput, stream);
        this.introspectiveInheritance.copyRegionFrom(
                introspectiveOutput, OUTPUT_DOMAIN.getInheritanceInfoSpan(),
                new Span(0, cLen) {}, stream);

        // [流程八] 反思层训练：写权重。
        introspectiveTrainingContext.execute(introspectiveAnchor, dtMillis, stream);
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

    /**
     * 关闭 Urana 意识体，停止快慢双工作线程并释放所有 GPU 资源。
     * 最多各等待 1.5 秒让两线程自然结束本轮，超时则中断后最多再等 0.5 秒。
     */
    @Override
    public void shutdown() {
        if (!running) return;
        running = false;
        joinWorker(fastWorkerThread, "UranaFastWorker");
        joinWorker(slowWorkerThread, "UranaSlowWorker");
        fastWorkerThread = null;
        slowWorkerThread = null;
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
     * dt 由快环自行用 nanoTime 差值计算。首轮 dt=0。受 fastMinDtMillis 节流。
     */
    private void runFastLoop() {
        long lastRunStartNanos = 0;
        while (running && !Thread.currentThread().isInterrupted()) {
            long now = System.nanoTime();
            long dtMillis = (lastRunStartNanos == 0) ? 0 : (now - lastRunStartNanos) / 1_000_000;
            lastRunStartNanos = now;

            try {
                runFastTick(currentFeeling, dtMillis);
            } catch (Exception e) {
                LOGGER.error("[Urana][Fast] 工作线程 runFastTick 异常", e);
            }

            throttle(fastMinDtMillis, lastRunStartNanos);
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

            throttle(slowMinDtMillis, lastRunStartNanos);
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
        return feelingSize;
    }

    @Override
    public int behaviorSize() {
        return behaviorSize;
    }

    private Span fullSpan(VectorBase vector) {
        return new Span(0, vector.size()) {
        };
    }

    @Override
    public void close() throws Exception {
        // 在此释放所有 AutoCloseable 资源
        prospectiveAnchor.close();
        prospectiveInference.close();
        prospectiveGradCell.close();
        retrospectiveAnchor.close();
        retrospectiveInference.close();
        retrospectiveGradCell.close();
        introspectiveAnchor.close();
        introspectiveInference.close();
        introspectiveTrainingContext.close();

        // ✅ 释放共享网络（nn 实现自管其内部资源：权重/IO/梯度/target）
        if (nn != null)
            nn.close();

        // 补充释放跨轮状态
        if (prospectiveInheritance != null)
            prospectiveInheritance.close();
        if (retrospectiveInheritance != null)
            retrospectiveInheritance.close();
        if (introspectiveInheritance != null)
            introspectiveInheritance.close();
        if (lastRetrospectiveFeeling != null)
            lastRetrospectiveFeeling.close();

        // 释放痕迹快照三缓冲
        if (traceFeeling != null) {
            for (VectorBase v : traceFeeling) {
                if (v != null) v.close();
            }
        }
        if (traceBehavior != null) {
            for (VectorBase v : traceBehavior) {
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
