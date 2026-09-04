package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.core.diagnostics.MemoryDiagnostics;
import com.github.magif1712.smarter_touhou_maids.core.execution.RefreshRequest;
import com.github.magif1712.smarter_touhou_maids.core.execution.event.Event;
import com.github.magif1712.smarter_touhou_maids.core.execution.stream.Stream;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.core.PossessionManager;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.IAgent;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterClientService;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamStore;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.IAiSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.debug.EffectorDebugHook;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.ActionIntent;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.IEffector;
import com.github.magif1712.smarter_touhou_maids.core.execution.MappedGenerationBuffer;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.ISensor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.network.ServerboundActionIntentPacket;
import com.github.magif1712.smarter_touhou_maids.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Smarter 模式的默认 {@link IAgent} 实现：感受器采集 + AI 运行 + 效应器解码 + 发包的完整链路。
 * <p>
 * 本类的所有业务逻辑搬家自原 {@link SmarterClientService}（拆分 agent 抽象后，SmarterClientService
 * 变为薄容器，业务逻辑下沉到本类）。
 * <p>
 * <b>三注入模式</b>（镜像 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.ProcessAiSystem}
 * 只注入 IProcessSystem）：本类直接使用 {@link IAiSystem}（思考）、{@link ISensor}（感知）、
 * {@link IEffector}（行动）三个抽象，构造注入。换 ai / sensor / effector 中任何一个时本类零改动
 * （真善美第2条：模式1 即便换了一种模式2 也可以不改代码就可以正确运行）。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第1条"真"</b>：本类直接使用 ai/sensor/effector，故注入三者。不直接使用 process/nn/
 *       vision/possession/muscle，故不持它们——换它们是 ai/sensor/effector 内部的事。</li>
 *   <li><b>第2条</b>：C 中"smarter agent = 感知→思考→行动"是这种 agent 的模式。
 *       感知细节（glBlitFramebuffer/bit 平面/附身）、思考细节（流程/nn）、行动细节（张力/迟滞）
 *       分别藏在 sensor/ai/effector 实现里，不进 IAgent 接口，也不进本类。</li>
 *   <li><b>第3条</b>：把"agent 可选 + 内部三件可替换"这个不实在的约束，实在化为注入的接口引用。</li>
 * </ul>
 * <p>
 * <b>资源归属</b>：feelingBuffer / cudaStream / visionEvent / MappedGenerationBuffer / behaviorScratch 是 agent
 * 创建并拥有的共享资源（ai 与 sensor 共用 feelingBuffer/cudaStream/visionEvent）。sensor 的捕获专用资源
 * （快照纹理）由 sensor 自管，effector 的肌肉状态由 effector 自管——本类只调它们的 awaken/shutdown。
 * <p>
 * <b>生命周期</b>：由 {@link SmarterClientService} 管理。SmarterClientService.init() 调
 * {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.AgentFactory#create} 创建本类
 * （已注入 ai+sensor+effector），再调 {@link #awaken} 创建共享资源并唤醒三者。
 * <p>
 * <b>dt 调试状态</b>：per-maid 存 ParamStore（maid NBT，随存档走，网络同步，消除 global state）；
 * {@link #current} 跟踪运行中实例，供 {@link #applyDtDebug} 即时应用到运行中的 AI。
 * 由 GUI（经 UranaProcessFactory 声明的 ParamOption）切换——dt 调试是反射弧系统代理的内部模式，不进 {@link IAgent} 接口。
 */
public class ReflexArcSystemAgent implements IAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger("ReflexArcSystem");

    private final IAiSystem ai;
    private final ISensor sensor;
    private final IEffector effector;

    /**
     * 感觉缓冲区（共享资源，agent 拥有）。载体类型（BoolVector/FloatVector）由 ai 链的
     * nn 家族定义（BNN→位平面，CNN→RGB float），经 {@code ai.newFeelingBuffer()} 取得——
     * agent 不感知具体载体（无类型开关），向下转型知识留在解码器内部。
     */
    private VectorBase feelingBuffer;
    private Stream cudaStream;
    /**
     * 视觉采集完成事件（共享资源，agent 拥有）。
     * <p>
     * 感受器在 cudaStream 上写完 feelingBuffer 后 record 此 event；
     * AI 在每轮开头 waitEvent，确保读到完整视觉数据。
     */
    private Event visionEvent;

    /**
     * 感觉刷新请求（共享资源，agent 拥有，纯 host 对象无 close）。
     * <p>
     * 拉模型握手：AI 每轮开头 request，感受器在渲染线程每帧 consume——
     * 有请求才编码，保证"编码次数 ≤ 消费次数"。构造即置位：首轮无条件编码一次。
     */
    private RefreshRequest feelingRefresh;

    /**
     * 行为产出通道（意识-外周边界对象）。
     * 持有 mapped behavior buffer + generation 计数器。由外周创建并注入 AI 系统，
     * AI 用其 producer 面（写入 buffer + publish），本类用其 consumer 面
     * （getGeneration / readTo）零 CUDA 调用消费。
     */
    private MappedGenerationBuffer behaviorChannel;

    /**
     * 池化的 behavior host 暂存（256 bit = 8 个 int）。主线程在 onClientTick 里，
     * 当 generation 变化时从 behaviorChannel 读到此缓冲，交效应器解码。
     */
    private int[] behaviorScratch;

    /**
     * 上次消费的 behavior generation 值。用于检测 AI 是否产出了新 behavior。
     * 主线程零 CUDA 调用：仅读 behaviorChannel.getGeneration()（纯 host 内存读）。
     */
    private int lastGen = -1;

    /**
     * 最近一次发包目标 maid 的实体 id。shutdown 时用它发一帧零 ActionIntent，
     * 让 maid 停止（避免客户端停发后服务端继续执行上一帧陈旧 intent）。
     */
    private int lastMaidId = 0;

    /**
     * dt 调试开关的 per-maid NBT key（存 ParamStore，随 maid 存档走）。
     * <p>
     * 状态不再以 static 字段留存（消除 global state，真善美第1条"善"），
     * 改存 ParamStore（maid NBT，per-maid，网络同步）。本类只持 key + 应用方法：
     * {@link #isDtDebugEnabled} 读 ParamStore，{@link #applyDtDebug} 写后即时应用到运行中 AI。
     */
    public static final String KEY_DT_DEBUG_ENABLED = "dtDebugEnabled";

    /**
     * 当前 agent 实例引用（static：供 {@link #applyDtDebug} 即时应用到运行中的 AI）。
     * awaken 时赋值，shutdown 时清空。
     */
    private static ReflexArcSystemAgent current = null;

    /**
     * 三注入构造函数：直接注入本类使用的三个抽象。换任何一个时本类零改动。
     *
     * @param ai       AI 系统抽象（思考机制，如 ProcessAiSystem，内部已注入 process/nn）。
     * @param sensor   感受器抽象（感知机制，如 PossessionSensor，内部编排 vision/possession）。
     * @param effector 效应器抽象（行动机制，如 BionicMuscleEffector，内部含 muscle/semantics）。
     */
    public ReflexArcSystemAgent(IAiSystem ai, ISensor sensor, IEffector effector) {
        this.ai = ai;
        this.sensor = sensor;
        this.effector = effector;
    }

    @Override
    public void awaken() {
        // 跟踪当前实例：供 applyDtDebug 即时应用到运行中的 AI（无需等重建）。
        current = this;
        // 感觉缓冲区：载体契约来自 ai 链（nn 家族定义载体：CNN→FloatVector，BNN→BoolVector）。
        // agent 不感知具体载体类型（无类型开关），载体类型知识留在 nn 家族与解码器内部。
        this.feelingBuffer = this.ai.newFeelingBuffer();
        this.behaviorScratch = new int[this.ai.behaviorSize() / 32];
        this.cudaStream = new Stream();
        // 视觉采集完成事件：感受器写完 feelingBuffer 后在 cudaStream 上 record，
        // AI 每轮开头 waitEvent 跨流等待，替代旧的 NULL 流隐式屏障。
        this.visionEvent = new Event();
        // 感觉刷新请求（拉模型握手）：AI 每轮 request，感受器 consume 后才编码。
        this.feelingRefresh = new RefreshRequest();

        // 行为产出通道：buffer 载体由 ai 链的 nn 家族决定（CNN→FloatVector，BNN→BoolVector），
        // 经 ai.newBehaviorBuffer() 取得——agent 不感知具体载体（无类型开关）。
        // generation 计数器始终 mapped（零拷贝读取），同步语义不变。
        this.behaviorChannel = new MappedGenerationBuffer(this.ai.newBehaviorBuffer());

        // 唤醒感受器：注入共享资源（feelingBuffer/cudaStream/visionEvent），sensor 自建快照纹理。
        this.sensor.awaken(this.feelingBuffer, this.cudaStream, this.visionEvent);
        // 注入感觉刷新请求（拉模型握手）：AI 每轮 request，感受器 consume 后才解码。
        // 走 ISensor 可选能力（default 方法），不支持拉模型的感受器（原初分支）自动忽略。
        this.sensor.setRefreshRequest(this.feelingRefresh);
        // 注入视觉解码器：由 ai 链的 nn 家族贡献（定义感觉载体者同时提供解码器，天然配对），
        // 感受器采集快照后按需调用。注入序：awaken → setRefreshRequest → setVisionEncoder
        // （感受器在 setVisionEncoder 做装配期校验：解码器↔缓冲尺寸契约 + 拉模型握手就位）。
        this.sensor.setVisionEncoder(this.ai.newVisionEncoder());
        // 唤醒效应器：初始化肌群/拮抗对/迟滞状态/复用 intent。
        this.effector.awaken();

        // 应用 per-maid 的 dt 调试状态到新 AI 实例（存 ParamStore，随 maid 存档走）。
        // maid 从 PossessionManager 取——awaken 在 smarterReady=true（已附身）时调用，
        // getPossessedMaid 必非空。null 时 isDtDebugEnabled fallback false（默认关）。
        EntityMaid dtMaid = PossessionManager.INSTANCE.getPossessedMaid();
        this.ai.setDtDebugEnabled(isDtDebugEnabled(dtMaid));
        // 唤醒意识体：启动内部工作线程持续运转，
        // 注入感觉缓冲区引用供工作线程每轮读取（AI 与 Minecraft tick 解耦）。
        this.ai.awaken(this.feelingBuffer, this.visionEvent, this.behaviorChannel);
        // 注入感觉刷新请求：AI 快环每轮开头 request（拉模型），经 IAiSystem 可选能力转发。
        this.ai.setRefreshRequest(this.feelingRefresh);

        // 复位 generation 消费游标，确保二次附身时首轮必读。
        this.lastGen = -1;

        LOGGER.info("[ReflexArc] 初始化完成 (feeling={} 载体单位, behavior={} bits)",
                this.ai.feelingSize(), this.ai.behaviorSize());
        logGpuMemory("init完成");
    }

    @Override
    public void onClientTick() {
        if (ai == null) {
            return;
        }
        EntityMaid maid = PossessionManager.INSTANCE.getPossessedMaid();
        if (maid == null) {
            return;
        }
        // 激活条件由 SmarterClientService 经 isActive() 守卫，本方法仅在激活上下文中调用。
        lastMaidId = maid.getId();
        try {
            // 读 generation（纯 host 内存读，零 CUDA 调用，不 flush WDDM 命令缓冲）。
            // generation 变化 = AI 已产出新 behavior 且 GPU 已写完 buffer（流内有序保证）。
            int gen = behaviorChannel.getGeneration();
            if (gen != lastGen) {
                lastGen = gen;
                // 行为读取：载体类型由 ai 链决定（BoolVector mapped 零拷贝 / FloatVector sync D2H），
                // ai.readBehaviorTo 把载体数据转为 effector 期望的 bit-packed int[]（无类型开关）。
                ai.readBehaviorTo(behaviorChannel.getBuffer(), behaviorScratch, 0L);
            }
            // fresh=false（gen 未变）时 behaviorScratch 保持上次值，效应器继续低通滤波（肌肉保持张力）。
            // effector.tick 返回复用实例，立即序列化发包，不跨 tick 持有引用。
            ActionIntent intent = effector.tick(behaviorScratch);
            // 效应器调试观察点：在效应器产出 intent 后、发包前输出人话指令。
            // per-maid 调试开关存 ParamStore，log 内部读 maid 判断是否启用。
            EffectorDebugHook.INSTANCE.log(intent, maid);
            NetworkHandler.INSTANCE.sendToServer(new ServerboundActionIntentPacket(maid.getId(), intent));
        } catch (Exception e) {
            LOGGER.error("[ReflexArc] 效应器解码/发包异常", e);
        }
    }

    @Override
    public void onPostRender(int textureId, int texWidth, int texHeight) {
        // 未激活（未附身）不采集视觉：PossessionSensor 需 maid 上下文，未附身无 maid 时空采集无意义。
        if (!isActive()) {
            return;
        }
        // 感受器采集在 cudaStream 上异步执行，与 AI 的专用流并发：
        // AI 重计算 kernel 与 D2D/H2D 拷贝跑在 AI 流，两者不再因 NULL 流串行而互相挂起。
        // sensor→AI 的跨流数据可见性由 visionEvent 显式同步：采集完成后 record，
        // AI 每轮开头 waitEvent 等待，确保读到完整 feelingBuffer。
        try {
            sensor.capture(textureId, texWidth, texHeight);
        } catch (Exception e) {
            LOGGER.error("[ReflexArc] 视觉捕获异常", e);
        }
    }

    /**
     * ReflexArcSystemAgent 的激活条件：正在附身。
     * <p>
     * 本 agent 依赖附身前置——PossessionSensor 需通过附身锁定 maid 并采集其视角视觉，
     * 未附身时无 maid 上下文，无法感知→思考→行动。故未附身时 smarter 不"启动"，
     * 原版 AI 正常运转（{@code MobServerAiStepSuppressMixin} 据 sync 后的激活标量放行）。
     * <p>
     * 其他 agent（无附身前置）可覆盖为默认 true：自动任务模式开启即激活。
     */
    @Override
    public boolean isActive() {
        return PossessionManager.INSTANCE.isPossessing();
    }

    /**
     * 将 agent 核心状态序列化到磁盘（在 {@link #shutdown} 释放显存前调用）。
     * <p>
     * <b>时机对称</b>（C3）：load 在 create（组装时），save 在 shutdown 前（显存未释放）。
     * 本方法只委托 ai save——ai 内部（UranaSystem.save）先停止工作线程保证一致快照，再 D2H + 写文件，
     * 不释放显存（释放由后续 {@link #shutdown} 负责）。
     * <p>
     * sensor/effector 是无状态叶子，无可持久化数据，本方法不碰它们。共享资源
     * （feelingBuffer/cudaStream/visionEvent/behaviorChannel）由 {@link #shutdown} 释放，本方法不碰。
     *
     * @param slot 持久化槽位（指向新版本目录，由 SmarterClientService.shutdown 创建）
     */
    @Override
    public void save(SaveSlot slot) {
        if (ai != null && slot != null) {
            ai.save(slot);
        }
    }

    @Override
    public void shutdown() {
        LOGGER.info("[ReflexArc] 关闭 ReflexArcSystem...");
        // 先清空 current：防止 applyDtDebug 在关闭过程中再向正在 shutdown 的 AI 下发状态。
        current = null;
        // 停止 AI 工作线程并释放其内部资源（join 工作线程，最多 1.5s+0.5s）。
        // shutdown 内部已调用 close()，且会先 join 工作线程，确保不再并发访问 feelingBuffer / behaviorChannel / visionEvent。
        if (ai != null) {
            ai.shutdown();
        }
        // MappedGenerationBuffer 必须在 ai.shutdown() join 工作线程之后关闭：
        // 工作线程每轮写入 behaviorChannel 的 buffer + publish，join 完成后无人再访问它，方可安全销毁。
        if (behaviorChannel != null) {
            try { behaviorChannel.close(); } catch (Exception ignored) {}
            behaviorChannel = null;
        }
        // 感受器清理：关闭捕获专用资源（快照纹理）。共享引用（feelingBuffer/cudaStream/visionEvent）
        // 由本类释放，sensor 只清自身资源。
        if (sensor != null) {
            try { sensor.shutdown(); } catch (Exception ignored) {}
        }
        // visionEvent 必须在 ai.shutdown() join + sensor.shutdown() 之后关闭：
        // 工作线程每轮 waitEvent(visionEvent)，sensor capture 时 record——两者都停后无人再访问它。
        if (visionEvent != null) {
            try { visionEvent.close(); } catch (Exception ignored) {}
        }
        if (cudaStream != null) {
            try { cudaStream.close(); } catch (Exception ignored) {}
        }
        if (feelingBuffer != null) {
            try { feelingBuffer.close(); } catch (Exception ignored) {}
        }
        // 效应器清理：复位张力/迟滞状态。
        if (effector != null) {
            try { effector.shutdown(); } catch (Exception ignored) {}
        }
        // 发一帧零 ActionIntent 让 maid 停止——
        // 客户端停发后服务端不再收到新 intent，若不发零帧，maid 会卡在上一帧陈旧动作上。
        if (lastMaidId != 0) {
            try {
                NetworkHandler.INSTANCE.sendToServer(
                        new ServerboundActionIntentPacket(lastMaidId, new ActionIntent()));
            } catch (Exception ignored) {}
            lastMaidId = 0;
        }
        lastGen = -1;
    }

    /**
     * 读 per-maid dt 调试开关状态（存 ParamStore maid NBT，随存档走）。
     * <p>
     * 供 awaken 应用初值、GUI（经 UranaProcessFactory 声明的 ParamOption）回显。
     * maid 为 null 时返回默认 false。
     */
    public static boolean isDtDebugEnabled(EntityMaid maid) {
        return Boolean.parseBoolean(
                ParamStore.INSTANCE.getString(maid, KEY_DT_DEBUG_ENABLED, "false"));
    }

    /**
     * 写 per-maid dt 调试开关并即时应用到运行中的 AI。
     * <p>
     * 供 GUI（经 UranaProcessFactory 声明的 ParamOption commitText）调用：
     * 写 ParamStore + 应用到 {@link #current}（若非空），即时生效无需等代理重建。
     * 状态本身存 ParamStore（per-maid），本方法只负责应用——存与用分离（真善美第4条：
     * 把"开关状态"实在化为 ParamStore String，把"应用到 AI"实在化为本方法）。
     * <p>
     * maid 参数：当前 {@link #current} 是单例（对应当前附身 maid），GUI 改的 maid 即是它，
     * 故暂不校验匹配；签名保留 maid 备未来多 maid 场景校验。
     */
    public static void applyDtDebug(EntityMaid maid, boolean enabled) {
        if (current != null && current.ai != null) {
            current.ai.setDtDebugEnabled(enabled);
        }
    }

    /**
     * 打印当前 GPU 显存使用情况。轻量级 driver 查询，单次开销微秒级，可低频调用。
     */
    private void logGpuMemory(String tag) {
        try {
            long[] info = MemoryDiagnostics._getMemInfo();
            long free = info[0];
            long total = info[1];
            long used = total - free;
            LOGGER.info("[ReflexArc][VRAM] {} 用={} MB / 空闲={} MB / 总={} MB",
                    tag, used / (1024L * 1024L), free / (1024L * 1024L), total / (1024L * 1024L));
        } catch (Exception e) {
            LOGGER.warn("[ReflexArc][VRAM] {} 查询失败", tag, e);
        }
    }

    /**
     * debug 专用：暴露感觉缓冲区供 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.debug.VisionDebugHook} dump。
     * 载体类型由 ai 链的 nn 家族决定（BoolVector/FloatVector），debug hook 按载体分派 dump 逻辑。
     * 不进 {@link IAgent} 接口——附属 agent 若有不同的感受器系统，应有自己的 debug hook。
     */
    public VectorBase getFeelingBuffer() {
        return feelingBuffer;
    }
}
