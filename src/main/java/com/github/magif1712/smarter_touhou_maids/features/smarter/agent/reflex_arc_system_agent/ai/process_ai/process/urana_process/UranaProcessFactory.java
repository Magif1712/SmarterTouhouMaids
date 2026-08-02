package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug.DebugOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug.DebugPanelProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamPanelProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamStore;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ReflexArcSystemAgent;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.IProcessSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.ProcessFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.InputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.OutputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.Registry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Urana 流程系统的 {@link ProcessFactory} 实现。
 * <p>
 * <b>工厂自驱组装</b>（真善美第1条"真"）：本工厂直接使用 nn（UranaSystem 构造注入 INeuralNetwork），
 * 故自行查 {@code NnRegistry} 取下层 nn factory 创建 nn，再 new UranaSystem(nn, ...)。
 * <p>
 * <b>nn 尺寸归属 process 层</b>：inputSize/outputSize 是 urana 的 Domain 知识
 * （{@link InputVectorDomain#TOTAL_LENGTH} / {@link OutputVectorDomain#TOTAL_LENGTH}），
 * 由本工厂算出传给 nn factory。nn factory 不反向依赖 urana Domain。
 * <p>
 * <b>节律参数自取</b>（真善美第3条）：fastMinDt/slowMinDt 是 urana 双环特定的 per-maid 参数，
 * nbtKey + 默认值 + 范围由本工厂自备。factory 自己 parse + clamp（本工厂 private 方法），
 * 管道（{@link ParamStore}）只搬 String——值类型解读是 factory 消费层的关注点，不在管道层。
 * 与 GUI 同路径（{@link #getParamOptions} 也用 {@link ParamOption#persistable} 经 ParamStore 读写），
 * 单一数据源，无第二读路径。
 * <p>
 * <b>调试项</b>：实现 {@link DebugPanelProvider} 暴露 dt 调试开关。
 * dt 调试是 process 层（urana 双环节律）的内部模式。状态存储在 {@link ReflexArcSystemAgent} 的 static 字段
 * （跨附身会话持久 + 即时应用到运行中的 AI），getter/setter 指向其 static 方法——故附身前即可配置。
 * <p>
 * <b>参数项</b>：实现 {@link ParamPanelProvider} 暴露快/慢环最小间隔参数（per-maid）。
 * 这些参数是 urana 双环节律特有的，选别的 process 时自动消失（GUI 零改动）。
 */
public class UranaProcessFactory implements ProcessFactory, DebugPanelProvider, ParamPanelProvider {

    private static final String KEY_FAST_MIN_DT = "FastMinDtMillis";
    private static final String KEY_SLOW_MIN_DT = "SlowMinDtMillis";
    /** minDt 默认值：0=不限速（全速运转）。 */
    private static final long DEFAULT_MIN_DT = 0;
    /** minDt 合法范围：[0, 5000] 毫秒。clamp 由本工厂负责。 */
    private static final long MIN_DT_MIN = 0;
    private static final long MIN_DT_MAX = 5000;

    @Override
    public IProcessSystem create(CompoundTag config, EntityMaid maid) {
        // === 查 NnRegistry 取下层 nn factory（自驱组装）===
        INeuralNetwork nn = createNn(config);

        // === 读 urana 双环节律参数（factory 自己 parse + clamp）===
        // 值类型解读是 factory 消费层的关注点，管道只搬 String。
        // maid 为 null 时 ParamStore.getString 返回默认值（不限速）。
        long fastMinDt = parseClampDt(
                ParamStore.INSTANCE.getString(maid, KEY_FAST_MIN_DT, String.valueOf(DEFAULT_MIN_DT)));
        long slowMinDt = parseClampDt(
                ParamStore.INSTANCE.getString(maid, KEY_SLOW_MIN_DT, String.valueOf(DEFAULT_MIN_DT)));

        // === 尺寸是 urana Domain 知识，由本工厂算出 ===
        return new UranaSystem(nn,
                InputVectorDomain.FEELING_SPAN_LENGTH,
                OutputVectorDomain.BEHAVIOR_SPAN_LENGTH,
                fastMinDt, slowMinDt);
    }

    /**
     * 从 config 读 nnId → 查 NnRegistry → 用 urana Domain 算尺寸 → 创建 nn。
     * nnId 缺失/非法时回退 NnRegistry 默认 entry（{@link Registry#resolve} 内置 fallback）。
     */
    private INeuralNetwork createNn(CompoundTag config) {
        Registry<?> nnRegistry = RegistryManager.INSTANCE.get(RegistryIds.NN);
        RegistryEntry<?> nnEntry = nnRegistry.resolve(config.getString(RegistryIds.NN.toString()));
        NnFactory nnFactory = (NnFactory) nnEntry.getFactory();

        // nn 尺寸由 process 层 Domain 算出（不是 nn 知识，不是外周知识）
        int inputSize = InputVectorDomain.TOTAL_LENGTH;
        int outputSize = OutputVectorDomain.TOTAL_LENGTH;
        return nnFactory.create(inputSize, outputSize);
    }

    @Override
    public List<DebugOption> getDebugOptions() {
        return List.of(
                DebugOption.onOff(
                        Component.translatable("debug.smarter_touhou_maids.dt"),
                        Component.translatable("debug.smarter_touhou_maids.dt.tooltip"),
                        ReflexArcSystemAgent::isDtDebugEnabledStatic,
                        ReflexArcSystemAgent::setDtDebugEnabledStatic));
    }

    @Override
    public List<ParamOption> getParamOptions() {
        // 与 create() 同 nbtKey + 默认值 + 范围，单一数据源。
        // textProcessor 在 commit 时 parse + clamp + String.valueOf——管道只调 textProcessor，不感知 long。
        return List.of(
                ParamOption.persistable(
                        Component.translatable("option.smarter_touhou_maids.fast_min_dt"),
                        Component.translatable("option.smarter_touhou_maids.fast_min_dt.tooltip"),
                        KEY_FAST_MIN_DT, String.valueOf(DEFAULT_MIN_DT),
                        (maid, text) -> String.valueOf(parseClampDt(text))),
                ParamOption.persistable(
                        Component.translatable("option.smarter_touhou_maids.slow_min_dt"),
                        Component.translatable("option.smarter_touhou_maids.slow_min_dt.tooltip"),
                        KEY_SLOW_MIN_DT, String.valueOf(DEFAULT_MIN_DT),
                        (maid, text) -> String.valueOf(parseClampDt(text))));
    }

    /**
     * 解析 minDt 文本：try parse long → clamp 到 [MIN_DT_MIN, MIN_DT_MAX] → 失败返回 DEFAULT_MIN_DT。
     * <p>
     * 值类型解读是 factory 消费层的关注点（真善美第3条）：不抽成集中工具类（避免每加值类型改工具类），
     * 本工厂自管自己的 long 解读。create() 读参数与 getParamOptions() 声明 textProcessor 共用此方法，单一数据源。
     */
    private static long parseClampDt(String text) {
        try {
            long v = Long.parseLong(text.trim());
            return Math.max(MIN_DT_MIN, Math.min(MIN_DT_MAX, v));
        } catch (NumberFormatException e) {
            return DEFAULT_MIN_DT;
        }
    }
}
