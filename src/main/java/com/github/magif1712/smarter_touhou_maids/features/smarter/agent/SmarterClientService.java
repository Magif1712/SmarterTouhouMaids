package com.github.magif1712.smarter_touhou_maids.features.smarter.agent;

import com.github.magif1712.smarter_touhou_maids.features.maid.compat.task.AutoTask;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlotFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.PersistenceConfigProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.WorldPersistenceDir;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.Registry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryManager;
import com.github.magif1712.smarter_touhou_maids.features.smarter.state.MaidSmarterState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.UUID;

/**
 * smarter 模式的客户端入口与生命周期容器（薄容器，委托 {@link IAgent} 执行业务逻辑）。
 * <p>
 * 本类是 Forge 事件总线接入点（{@link #onClientTick}）与渲染钩子接入点
 * （{@link #onPostRender}），负责：
 * <ul>
 *   <li>smarter 就绪生命周期管理：smarterReady（=maid 任务模式为自动任务，{@link AutoTask#isAutoTask}）
 *       变化触发 init / shutdown</li>
 *   <li>从 {@link RegistryIds#AGENT} 顶层 registry 查 factory 创建 agent（工厂自驱组装下层）</li>
 *   <li>委托 agent 执行 onClientTick / onPostRender（经 {@link IAgent#isActive()} 守卫）</li>
 *   <li>激活状态 sync：agent isActive 边界变化时 sync 到服务端（替代旧 smarter UI 开关 sync）</li>
 * </ul>
 * <p>
 * <b>启用 vs 激活</b>（真善美第2条）：
 * <ul>
 *   <li><b>启用</b>（smarterReady）= maid 任务模式为"自动任务"（通用，{@link AutoTask#isAutoTask}）。
 *       maid.getTask 经 SynchedEntityData 自动双端同步，客户端可靠读取；不再有独立 UI 开关。</li>
 *   <li><b>激活</b>（active）= agent 自决（{@link IAgent#isActive()}，特有）。
 *       ReflexArcSystemAgent 因依赖附身前置而 active=附身；其他 agent 可直接 active=true。</li>
 * </ul>
 * 服务端 {@code MobServerAiStepSuppressMixin} 只读 sync 后的激活标量，不依赖下层激活条件
 * （真善美第3条：换 agent 激活条件时服务端零改动）。
 * <p>
 * <b>maid 来源</b>：本类不直接依赖任何具体 maid 来源（如附身），而是经 {@link MaidSource} 抽象
 * 获取 maid。具体实现由 agent 的 sensor 子系统在 client setup 阶段注入（如 reflex_arc 的 possession
 * 注入 {@code getPossessedMaid}）。换 agent/sensor 时 maid 来源随之切换，本类零改动
 * （真善美第3条：Y 是 X 的模式，Y 换模式时 X 不改代码）。
 * <p>
 * 本类只关心"何时创建/销毁 agent、何时委托执行"。视觉采集、AI 运行、效应器解码、发包、
 * 调试开关等都是 agent 的内部模式，不属本类。附属 agent 的 debug hook 通过
 * {@link #getAgent()} 获取当前 agent 实例并自行向下转型访问。
 */
@OnlyIn(Dist.CLIENT)
public class SmarterClientService {

    public static final SmarterClientService INSTANCE = new SmarterClientService();

    private static final Logger LOGGER = LoggerFactory.getLogger("ReflexArcSystem");

    private IAgent agent;
    private boolean initialized = false;
    /** 上一 tick 的 smarter 就绪状态，检测 true→false 触发 shutdown。 */
    private boolean wasSmarterReady = false;
    /** 上一 tick 的 agent 激活状态，检测边界变化触发 sync。 */
    private boolean wasActive = false;
    /** 最后一次 sync 的 maidUUID，供 shutdown 时 sync active=false（此时 maid 可能已 null）。 */
    private UUID lastMaidUUID = null;

    /**
     * 持久化上下文（init 时 maid 可用算好存储，shutdown 时 maid 可能已 null 但仍需 save/prune）。
     * <p>
     * <b>为何存 EntityMaid 引用</b>：shutdown 时取消附身后 maidSource.get() 可能返回 null，
     * 但持久化仍需进行（save 训练成果 + prune 旧版本）。配置存 ParamStore（maid NBT，per-maid），
     * 需 maid 读配置——故 init 时存 maid 引用，shutdown 用引用读 ParamStore。
     * maid 实体对象在 shutdown 时仍存活（Java 引用未释放），NBT 可读（即使已从 level 移除）。
     * <p>
     * <b>pathDir 是 world-aware 的</b>：由 {@link WorldPersistenceDir#computeBaseDir()} 在 init 时算
     * （单人=世界存档目录，多人=GAMEDIR/servers/&lt;ip&gt;），再拼 maidUUID + pathToken 得到。
     * shutdown 时直接用此 pathDir 调 {@link SaveSlotFactory}——工厂不感知世界（真善美第3条）。
     * <p>
     * <b>sync 与持久化分离</b>：{@link #lastMaidUUID} 用于 sync（服务端只运行一个世界，UUID 在服务端
     * 世界内唯一，世界上下文对服务端隐式）；{@link #persistMaid} + {@link #persistPathDir}
     * 用于持久化（per-maid 配置 + world-aware 路径）。两个关注点各自用各自合适的标识，不混用。
     */
    private SaveSlot saveSlot = null;
    private Path persistPathDir = null;
    /** init 时的 maid 引用，供 shutdown 读 ParamStore 持久化配置（shutdown 时 maidSource 可能已 null）。 */
    private EntityMaid persistMaid = null;

    /**
     * maid 来源（客户端）。由具体 agent 的 sensor 子系统在 client setup 阶段注入
     * （如 reflex_arc 的 possession 注入 getPossessedMaid）。上层不依赖任何具体 maid 来源
     * （真善美第3条：换 sensor/agent 时 maid 来源随之切换，本类零改动；第4条：把"maid 来源"
     * 这个不实在的概念实在化为接口）。
     */
    private MaidSource maidSource = () -> null;

    /** 注入 maid 来源（供 reflex_arc 等 agent 的 sensor 子系统在 client setup 调用）。 */
    public void setMaidSource(MaidSource maidSource) {
        this.maidSource = maidSource;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        EntityMaid maid = maidSource.get();
        // smarter 就绪 = maid 任务模式为"自动任务"（maid 来源经 MaidSource 注入，如 reflex_arc
        // 的 possession 注入 getPossessedMaid）。maid.getTask() 经 SynchedEntityData 自动双端同步，
        // 客户端可靠读取（不依赖不同步的 persistentData）。maid 为 null 时
        // isAutoTask=false → smarterReady=false（agent 不创建，原版 AI 正常）。
        boolean smarterReady = AutoTask.isAutoTask(maid);

        // 生命周期：smarterReady 边界变化触发 init / shutdown。
        // false→true：init 创建 agent（工厂自驱组装下层各层）；
        // true→false：shutdown 销毁 agent 并 sync active=false。
        if (!wasSmarterReady && smarterReady) {
            init();
        }
        if (wasSmarterReady && !smarterReady) {
            shutdown();
        }
        wasSmarterReady = smarterReady;

        if (!smarterReady || agent == null) {
            return;
        }

        // 记录 maidUUID 供 shutdown 时 sync active=false（取消附身后 maid 可能立即 null）。
        lastMaidUUID = maid.getUUID();

        // 激活 sync：isActive 边界变化时 sync 到服务端（替代旧 smarter UI 开关 sync）。
        // 服务端 MobServerAiStepSuppressMixin 据此决定是否抑制原版 AI。
        boolean active = agent.isActive();
        if (wasActive != active) {
            SmarterClientState.INSTANCE.setSmarterModeEnabled(maid, active);
            wasActive = active;
        }
        if (!active) {
            // 未激活（如未附身）不 tick：原版 AI 正常，smarter 不发包。
            return;
        }
        agent.onClientTick();
    }

    public void onPostRender(int textureId, int texWidth, int texHeight) {
        // init 由 onClientTick 的 smarterReady 触发（避免未就绪时懒创建 agent）。
        // onPostRender 仅在已初始化 + 激活时委托采集视觉（与 onClientTick 守卫一致）。
        // 若 onPostRender 先于首 tick（渲染钩子早于 tick），首帧跳过采集，下 tick init 后恢复。
        if (initialized && agent != null && agent.isActive()) {
            agent.onPostRender(textureId, texWidth, texHeight);
        }
    }

    private void init() {
        LOGGER.info("[ReflexArc] 初始化 ReflexArcSystem...");
        EntityMaid maid = maidSource.get();

        // 组装 config：只复制 maid 的 AiModes（各层 mode id）。
        // maid 为 null 时 config 为空，registry.resolve("") fallback 到各层默认 entry（旧存档兼容）。
        // 真善美第3条：外周不再硬编码下层特定参数（如 urana 的 minDt key）进 config——
        // per-maid 参数由各层 factory 经 maid 查 ParamStore 自取，nbtKey 由该层自备。换 process 时外周零改动。
        CompoundTag config = new CompoundTag();
        if (maid != null) {
            config.merge(MaidSmarterState.getAiModes(maid));
        }

        // 持久化槽位（C3/C4）：load 时取最新已有版本（无则新版本路径，各层 load 见文件缺失则保持默认）。
        // maid 非空时（smarterReady=true 必然 maid 非空，因 isAutoTask(null)=false）算好 world-aware
        // baseDir → pathDir，存储供 shutdown 用（shutdown 时 maid 可能已 null，但 save/prune 仍需进行）。
        SaveSlot slot = null;
        if (maid != null) {
            String token = SmarterLayerWalker.pathToken(maid);
            Path baseDir = WorldPersistenceDir.computeBaseDir();
            Path pathDir = baseDir.resolve(maid.getUUID().toString()).resolve(token);
            slot = SaveSlotFactory.latestOrNew(pathDir);
            this.persistPathDir = pathDir;
            this.persistMaid = maid;
        }
        this.saveSlot = slot;

        // 工厂自驱组装：外周只调顶层 agent factory，下层（ai→process→nn）组装自驱。
        // config + maid + slot 透传，各层 factory 各取所需（config 读 mode id，maid 读 per-maid 参数，
        // slot 供 nn/process load 持久化数据）。
        Registry<?> agentRegistry = RegistryManager.INSTANCE.get(RegistryIds.AGENT);
        RegistryEntry<?> agentEntry = agentRegistry.resolve(config.getString(RegistryIds.AGENT.toString()));
        AgentFactory agentFactory = (AgentFactory) agentEntry.getFactory();
        this.agent = agentFactory.create(config, maid, slot);
        this.agent.awaken();

        this.initialized = true;
        LOGGER.info("[ReflexArc] 初始化完成");
    }

    public void shutdown() {
        if (!initialized) return;
        LOGGER.info("[ReflexArc] 关闭 ReflexArcSystem...");
        // 先 sync active=false（确保服务端恢复原版 AI），用 lastMaidUUID（此时 getPossessedMaid 可能已 null）。
        // 经 SmarterClientState.setSmarterModeEnabled(UUID,...) 发包，服务端 MaidSmarterState.setEnabled(false)，
        // MobServerAiStepSuppressMixin 放行 serverAiStep，原版 AI 复原。
        if (wasActive && lastMaidUUID != null) {
            SmarterClientState.INSTANCE.setSmarterModeEnabled(lastMaidUUID, false);
            wasActive = false;
        }
        // 持久化 save（C3 时机对称：在 agent.shutdown 释放显存前）。
        // 用 init 时存储的 persistMaid 读 per-maid 配置（shutdown 时 maidSource 可能已 null，
        // 但 persistMaid 引用仍存活，ParamStore 可读 maid NBT）。
        if (agent != null && persistPathDir != null && persistMaid != null) {
            boolean shouldSave = PersistenceConfigProvider.isPersistenceEnabled(persistMaid);
            if (shouldSave) {
                try {
                    SaveSlot newSlot = SaveSlotFactory.newVersion(persistPathDir);
                    agent.save(newSlot);
                } catch (Exception e) {
                    LOGGER.error("[ReflexArc] 持久化 save 失败", e);
                }
                SaveSlotFactory.pruneOldVersions(persistPathDir,
                        PersistenceConfigProvider.getMaxRetention(persistMaid));
            }
        }
        if (agent != null) {
            agent.shutdown();
            agent = null;
        }
        initialized = false;
        lastMaidUUID = null;
        saveSlot = null;
        persistPathDir = null;
        persistMaid = null;
    }

    /**
     * 获取当前 agent 实例（供 debug hook 向下转型访问 agent 内部状态）。
     * 未就绪时返回 null。
     */
    public IAgent getAgent() {
        return agent;
    }

    private SmarterClientService() {
    }
}
