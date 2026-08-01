package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug.DebugOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug.DebugPanelProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.LongParamOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamPanelProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ReflexArcSystemAgent;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.IProcessSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.ProcessFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.InputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.OutputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.core.PossessionManager;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.Registry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
 * <b>节律参数</b>：fastMinDt/slowMinDt 是 urana 双环特定的，从 config 读
 * （key = "FastMinDtMillis"/"SlowMinDtMillis"，与 MaidSmarterState 持久化 key 一致）。
 * 别的 process 实现的 factory 读自己需要的 key，忽略这两个。
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

    @Override
    public IProcessSystem create(CompoundTag config) {
        // === 查 NnRegistry 取下层 nn factory（自驱组装）===
        INeuralNetwork nn = createNn(config);

        // === 读 urana 双环节律参数（urana 特定，别的 process factory 不读这两个 key）===
        long fastMinDt = config.getLong(KEY_FAST_MIN_DT);
        long slowMinDt = config.getLong(KEY_SLOW_MIN_DT);

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
        return List.of(
                LongParamOption.of(
                        Component.translatable("option.smarter_touhou_maids.fast_min_dt"),
                        Component.translatable("option.smarter_touhou_maids.fast_min_dt.tooltip"),
                        PossessionManager.INSTANCE::getFastMinDtMillis,
                        PossessionManager.INSTANCE::setFastMinDtMillis),
                LongParamOption.of(
                        Component.translatable("option.smarter_touhou_maids.slow_min_dt"),
                        Component.translatable("option.smarter_touhou_maids.slow_min_dt.tooltip"),
                        PossessionManager.INSTANCE::getSlowMinDtMillis,
                        PossessionManager.INSTANCE::setSlowMinDtMillis));
    }
}
