package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original;

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
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.NnEncodingProfile;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.semantics.containers.io.InputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.semantics.containers.io.OutputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.AssemblyContext;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.OutSlot;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Urana 流程系统的 {@link ProcessFactory} 实现。
 * <p>
 * <b>工厂自驱组装</b>（真善美第1条"真"）：本工厂直接使用 nn（UranaSystem 构造注入 INeuralNetwork），
 * 故自行查 {@code NnRegistry} 取下层 nn factory 创建 nn，再 new UranaSystem(nn, ...)。
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
        // === nn 提供者子实例由 Resolver 按本分支声明的 child（"nn"）解析传入 ===
        // provider 模式：nn 实例化需要尺寸（process 层 Domain 知识），本工厂经
        // encodingProfile() 无实例查询剖面、建 domain 算总长后带参实例化。
        NnFactory nnFactory = ctx.child("nn", NnFactory.class);

        // === profile = nnFactory.encodingProfile()（无实例查询，破鸡生蛋）===
        NnEncodingProfile profile = nnFactory.encodingProfile();

        // === 用 profile 建 domain（urana 用 profile + 倍数关系算 span）===
        InputVectorDomain inputDomain = new InputVectorDomain(profile);
        OutputVectorDomain outputDomain = new OutputVectorDomain(profile);

        // === 创建 nn 实例（inputSize/outputSize 由 urana 算，nn 只接收 total 分配缓冲）===
        SaveSlot slot = ctx.saveSlot();
        INeuralNetwork nn = nnFactory.create(slot, inputDomain.totalLength(), outputDomain.totalLength());

        // === 读 urana 双环节律参数（factory 自己 parse + clamp）===
        long fastMinDt = parseClampDt(
                ParamStore.INSTANCE.getString(ctx.maid(), KEY_FAST_MIN_DT, String.valueOf(DEFAULT_FAST_MIN_DT)));
        long slowMinDt = parseClampDt(
                ParamStore.INSTANCE.getString(ctx.maid(), KEY_SLOW_MIN_DT, String.valueOf(DEFAULT_SLOW_MIN_DT)));

        // === domain 注入 UranaSystem ===
        UranaSystem urana = new UranaSystem(nn, inputDomain, outputDomain, fastMinDt, slowMinDt);

        // === load urana 自身跨会话状态（∇C/继承/休眠时间/锚点）===
        // 时机（C3）：nn 权重已由 nnFactory.create load；此处 load urana 层状态。
        // 在 awaken 前调用——fast/slow 工作线程尚未启动，无并发。
        urana.load(slot);

        // === 注入定期 save 配置（C6 崩溃恢复）===
        EntityMaid maid = ctx.maid();
        if (maid != null && slot != null) {
            Path pathDir = Path.of(slot.rootPath()).getParent();
            urana.setPeriodicSaveConfig(
                    () -> SaveSlotFactory.newVersion(pathDir),
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
     * 与 StandardBnnNnFactory 的 NN 权重声明叠加，路径任一声明 true 即默认开。
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
