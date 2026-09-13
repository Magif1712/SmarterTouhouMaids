package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug.DebugPanelProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamPanelProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamStore;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.PersistableProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.PersistenceConfigProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlotFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ReflexArcSystemAgent;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.IProcessSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.ProcessFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.AssemblyContext;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.OutSlot;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Urana 流程系统的 {@link ProcessFactory} 实现（新版，架构 process→mapper→nn）。
 * <p>
 * <b>工厂自驱组装</b>（真善美第1条"真"）：本工厂直接使用 mapper（UranaSystem 构造注入
 * {@link FittableMapper} 接口），故自行查 MapperRegistry 取下层 mapper factory 创建 mapper，
 * 再 new UranaSystem(mapper, ...)。mapper 内部持 nn——nn 是 mapper 的附庸，本工厂经 mapper 间接用 nn。
 * <p>
 * <b>依赖接口</b>（真善美第2条）：UranaSystem 期望 {@link FittableMapper} 接口而非具体类——
 * 附属模组可在 process→nn 之间插入实现该接口的装饰器层（如日志/量化/蒸馏），无需 mixin；
 * mapperFactory.create 返回何种 mapper 实例由 factory 自决，本工厂零改动地适配。
 * <p>
 * <b>三层解析时序</b>（每一步的输入都来自上一步的输出，不可调换）：
 * <ol>
 *   <li>MapperRegistry.resolve(config["mapper"]) → mapperEntry + FittableMapperFactory  按名解析映射器工厂</li>
 *   <li>mapperEntry.getSubRegistryId() → nnRegistryId              从 mapper entry 动态获得 NN registry id（不 import NN 常量）</li>
 *   <li>RegistryManager.get(nnRegistryId).resolve(config[nnRegistryId]) → NnFactory  按名解析 NN 工厂</li>
 *   <li>nnFactory.encodingProfile() → NnEncodingProfile            问 NN：你的载体怎么编码各语义对象？</li>
 *   <li>IODomain(profile) → inputDomain/outputDomain              据此推导 span 布局（编码方案）</li>
 *   <li>nnFactory.create(slot, inLen, outLen) → INeuralNetwork     用推导出的总长创建 NN 实例</li>
 *   <li>mapperFactory.create(nn, inputDomain, outputDomain) → FittableMapper  装配可拟合映射器</li>
 *   <li>UranaSystem(mapper, fastMinDt, slowMinDt)                流程系统——只认映射器</li>
 * </ol>
 * <p>
 * <b>nn 尺寸归属 process 层</b>：inputSize/outputSize 是 urana 的 Domain 知识
 * （{@code inputDomain.totalLength()} / {@code outputDomain.totalLength()}），
 * 由本工厂用 {@link NnFactory#encodingProfile()} 取 profile、建 domain 后算出传给 nn factory。
 * nn factory 不反向依赖 urana Domain。
 * <p>
 * <b>节律参数自取</b>（真善美第3条）：fastMinDt/slowMinDt 是 urana 双环特定的 per-maid 参数，
 * nbtKey + 默认值 + 范围由本工厂自备。factory 自己 parse + clamp（本工厂 private 方法），
 * 管道（{@link ParamStore}）只搬 String——值类型解读是 factory 消费层的关注点，不在管道层。
 * 与 GUI 同路径（{@link #getParamOptions} 也用 {@link ParamOption#persistable} 经 ParamStore 读写），
 * 单一数据源，无第二读路径。
 * <p>
 * <b>调试项</b>：实现 {@link DebugPanelProvider} 暴露 dt 调试开关（controlHint="toggle" 的 {@link ParamOption}）。
 * dt 调试是 process 层（urana 双环节律）的内部模式。状态 per-maid 存 {@link ParamStore}（随 maid 存档走），
 * 经 {@link ReflexArcSystemAgent#isDtDebugEnabled}/{@link ReflexArcSystemAgent#applyDtDebug} 读写——
 * commit 时写 ParamStore + 即时应用到运行中 AI，故附身前即可配置、改后即时生效。
 * <p>
 * <b>参数项</b>：实现 {@link ParamPanelProvider} 暴露快/慢环最小间隔参数（per-maid）。
 * 这些参数是 urana 双环节律特有的，选别的 process 时自动消失（GUI 零改动）。
 */
public class UranaProcessFactory implements ProcessFactory, DebugPanelProvider, ParamPanelProvider, PersistableProvider {

    private static final String KEY_FAST_MIN_DT = "FastMinDtMillis";
    private static final String KEY_SLOW_MIN_DT = "SlowMinDtMillis";
    /** 快环 minDt 默认值：0=不限速（全速运转）。 */
    private static final long DEFAULT_FAST_MIN_DT = 0;
    /** 慢环 minDt 默认值：100ms。慢环每轮梯度后留 GPU 空隙给 GL 命令执行，防止 cudaGraphicsMapResources 阻塞渲染线程。 */
    private static final long DEFAULT_SLOW_MIN_DT = 100;
    /** minDt 合法范围：[0, 5000] 毫秒。clamp 由本工厂负责。 */
    private static final long MIN_DT_MIN = 0;
    private static final long MIN_DT_MAX = 5000;

    @Override
    public void create(AssemblyContext ctx, /*->*/ OutSlot<IProcessSystem> out) {
        // === mapper 子实例由 Resolver 按本分支声明的 child（"mapper"）解析传入 ===
        // mapper 内部持 nn（nn 是 mapper 的附庸，mapper 工厂经 provider 模式自驱装好），
        // 本工厂不感知 nn——「我的附庸的附庸不是我的附庸」。
        FittableMapper mapper = ctx.child("mapper", FittableMapper.class);

        // === 读 urana 双环节律参数（factory 自己 parse + clamp）===
        // 值类型解读是 factory 消费层的关注点，管道只搬 String。
        // maid 为 null 时 ParamStore.getString 返回默认值（不限速）。
        long fastMinDt = parseClampDt(
                ParamStore.INSTANCE.getString(ctx.maid(), KEY_FAST_MIN_DT, String.valueOf(DEFAULT_FAST_MIN_DT)));
        long slowMinDt = parseClampDt(
                ParamStore.INSTANCE.getString(ctx.maid(), KEY_SLOW_MIN_DT, String.valueOf(DEFAULT_SLOW_MIN_DT)));

        // === mapper 注入 UranaSystem（profile 已下沉到 nn，domain 已下沉到 mapper）===
        // UranaSystem 期望 FittableMapper 接口——无需强转，支持装饰器层（真善美第2条：依赖接口）。
        UranaSystem urana = new UranaSystem(mapper, fastMinDt, slowMinDt);

        // === load urana 自身跨会话状态（∇C/继承/休眠时间/锚点）===
        // 时机（C3）：nn 权重已由 nn provider 的 create load；此处 load urana 层状态。
        // 在 awaken 前调用——fast/slow 工作线程尚未启动，无并发。
        SaveSlot slot = ctx.saveSlot();
        urana.load(slot);

        // === 注入定期 save 配置（C6 崩溃恢复）===
        // 工厂作为组装层，读 per-maid 持久化配置 + 创建槽位/清理回调，以函数式接口注入 urana。
        // urana 不感知 PersistenceConfigProvider/SaveSlotFactory（真善美第3条：依赖注入而非直接依赖）。
        // pathDir 从 slot 反推（slot.rootPath() = pathDir/v<ts>，取 parent）——本工厂不感知 baseDir 来源
        // （世界/服务器），只从已实在化的 slot 路径反推（真善美第3条 + 第4条）。
        // maid 或 slot 为 null 时（理论不发生，smarterReady=true 必然 maid 非空且 slot 已创建）跳过——
        // urana 用默认值（禁用定期 save）。
        EntityMaid maid = ctx.maid();
        if (maid != null && slot != null) {
            Path pathDir = Path.of(slot.rootPath()).getParent();
            urana.setPeriodicSaveConfig(
                    () -> SaveSlotFactory.newVersion(pathDir),
                    // 定时开关在 factory 层组合进 intervalProvider（关→0L 禁用）：
                    // urana 不感知"定时持久化开关"概念，只收"间隔(0=禁用)"（真善美第3条）。
                    // 间隔单位是毫秒（墙钟时间，非轮数）——per-maid 读 ParamStore。
                    () -> {
                        if (!PersistenceConfigProvider.isPersistenceEnabled(maid)) {
                            return 0L;
                        }
                        if (!PersistenceConfigProvider.isPeriodicSaveEnabled(maid)) {
                            return 0L;
                        }
                        return PersistenceConfigProvider.getSaveIntervalMillis(maid);
                    },
                    () -> SaveSlotFactory.pruneOldVersions(pathDir,
                            PersistenceConfigProvider.getMaxRetention(maid)));
        }

        out.set(urana);
    }

    @Override
    public List<ParamOption> getDebugOptions() {
        return List.of(
                ParamOption.of(
                        Component.translatable("debug.smarter_touhou_maids.dt"),
                        Component.translatable("debug.smarter_touhou_maids.dt.tooltip"),
                        maid -> String.valueOf(ReflexArcSystemAgent.isDtDebugEnabled(maid)),
                        (maid, text) -> {
                            ParamStore.INSTANCE.setString(maid, ReflexArcSystemAgent.KEY_DT_DEBUG_ENABLED, text);
                            ReflexArcSystemAgent.applyDtDebug(maid, Boolean.parseBoolean(text));
                        })
                        .withControlHint("toggle"));
    }

    @Override
    public List<ParamOption> getParamOptions() {
        // 与 create() 同 nbtKey + 默认值 + 范围，单一数据源。
        // textProcessor 在 commit 时 parse + clamp + String.valueOf——管道只调 textProcessor，不感知 long。
        return List.of(
                ParamOption.persistable(
                        Component.translatable("option.smarter_touhou_maids.fast_min_dt"),
                        Component.translatable("option.smarter_touhou_maids.fast_min_dt.tooltip"),
                        KEY_FAST_MIN_DT, String.valueOf(DEFAULT_FAST_MIN_DT),
                        (maid, text) -> String.valueOf(parseClampDt(text))),
                ParamOption.persistable(
                        Component.translatable("option.smarter_touhou_maids.slow_min_dt"),
                        Component.translatable("option.smarter_touhou_maids.slow_min_dt.tooltip"),
                        KEY_SLOW_MIN_DT, String.valueOf(DEFAULT_SLOW_MIN_DT),
                        (maid, text) -> String.valueOf(parseClampDt(text))));
    }

    /**
     * Urana 流程系统产生可持久化数据：∇C 跨轮梯度缓冲 ×3、继承信息 ×3、最后一轮开始时间。
     * <p>
     * 声明 true → 路径默认持久化开关为 true（用户可在 GUI 覆盖）。
     * 与 CnnNnFactory 的 NN 权重声明叠加，路径任一声明 true 即默认开。
     */
    @Override
    public boolean hasPersistableData() {
        return true;
    }

    /**
     * 解析 minDt 文本：try parse long → clamp 到 [MIN_DT_MIN, MIN_DT_MAX] → 失败返回 0（安全回退 = 不限速）。
     * <p>
     * 值类型解读是 factory 消费层的关注点（真善美第3条）：不抽成集中工具类（避免每加值类型改工具类），
     * 本工厂自管自己的 long 解读。create() 读参数与 getParamOptions() 声明 textProcessor 共用此方法，单一数据源。
     */
    private static long parseClampDt(String text) {
        try {
            long v = Long.parseLong(text.trim());
            return Math.max(MIN_DT_MIN, Math.min(MIN_DT_MAX, v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
