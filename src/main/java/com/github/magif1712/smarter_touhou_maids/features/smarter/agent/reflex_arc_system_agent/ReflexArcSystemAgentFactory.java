package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.maid.compat.task.AutoTask;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.AgentFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.IAgent;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamPanelProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.AiFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.IAiSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.EffectorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.IEffector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.ISensor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.SensorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.PossessionSensorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.Registry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Smarter agent 的 {@link AgentFactory} 实现。
 * <p>
 * <b>工厂自驱组装三件</b>（真善美第1条"真"）：ReflexArcSystemAgent 构造注入 ai+sensor+effector 三者，
 * 故本工厂直接使用这三个抽象，自行查三个 registry 取下层 factory 创建它们，再 new ReflexArcSystemAgent(ai, sensor, effector)。
 * 外周（SmarterClientService）不感知任何 registry 的存在——它只调本工厂。
 * <p>
 * sensor/effector 是与 ai 并列的叶子层（agent 下 sensor+ai+effector 三个子模式），各自独立查询、无递归
 * （它们 subRegistryId=null）。ai 仍按原 subRegistryId 链自驱组装 process/nn。
 * <p>
 * config 里各 id 缺失/非法时回退对应 registry 默认 entry（{@link Registry#resolve} 内置 fallback）。
 * <p>
 * 附属 agent 的工厂实现可不查这些 registry（如纯规则 agent 不需要 ai/sensor/effector），直接造自己的 agent。
 * <p>
 * <b>参数项</b>（实现 {@link ParamPanelProvider}）：暴露"允许附身"开关（per-maid 布尔）。
 * 附身是 ReflexArcSystemAgent 的 PossessionSensor 前置——本 agent 依赖附身锁定 maid 并采集其视角，
 * 故"允许附身"是本 agent 特有配置。换 agent 不实现 ParamPanelProvider 时自动消失（GUI 零改动），
 * 与 minDt 经 UranaProcessFactory 暴露同机制（真善美第2条：数据驱动、跟随 agent）。
 */
public class ReflexArcSystemAgentFactory implements AgentFactory, ParamPanelProvider {

    /**
     * 本分支在 AGENT registry 的 entry id（{@code AiModeDefaults} 注册 smarter 时引用）。
     * possession 等分支私有模式的守卫以此比对 maid 选中的 agent（多代理共存，D4 形态修正）。
     * dist 中立（客户端输入守卫 / 服务端附身请求守卫共用）。
     */
    public static final ResourceLocation AGENT_ID =
            new ResourceLocation(SmarterTouhouMaids.MOD_ID, "smarter");

    @Override
    public IAgent create(CompoundTag config, EntityMaid maid, SaveSlot slot) {
        // === 查 AiRegistry 取下层 ai factory（自驱组装 process/nn）===
        // 新版代理用 AI_SMARTER（独立 registry，只含 urana——与原初代理隔离，
        // 避免跨代理流程选择导致的不兼容组合）
        Registry<?> aiRegistry = RegistryManager.INSTANCE.get(RegistryIds.AI_SMARTER);
        RegistryEntry<?> aiEntry = aiRegistry.resolve(config.getString(RegistryIds.AI_SMARTER.toString()));
        AiFactory aiFactory = (AiFactory) aiEntry.getFactory();
        // 下层 ai factory 自驱组装其内部 process/nn（config + maid + slot 透传，各层各取所需）
        IAiSystem ai = aiFactory.create(config, maid, slot);

        // === 查 SensorRegistry 取下层 sensor factory（叶子，无递归）===
        Registry<?> sensorRegistry = RegistryManager.INSTANCE.get(RegistryIds.SENSOR);
        // sensor 选择：config 显式选择时用之；缺失/空时回退本代理的默认 sensor
        // （on_demand_possession_sensor，采集/解码分离的拉模型版），不依赖 registry 默认 entry
        // （那是原初代理的默认 possession_sensor，与本代理的 ai 链载体不配）——
        // 每个 agent 工厂自知其兼容默认（真善美第4条：默认选择实在化为工厂常量）。
        String sensorIdStr = config.getString(RegistryIds.SENSOR.toString());
        RegistryEntry<?> sensorEntry = (sensorIdStr == null || sensorIdStr.isEmpty())
                ? sensorRegistry.get(PossessionSensorFactory.SENSOR_ID)
                : sensorRegistry.resolve(sensorIdStr);
        if (sensorEntry == null) {
            throw new IllegalStateException(
                    "本代理默认感受器未注册: " + PossessionSensorFactory.SENSOR_ID);
        }
        SensorFactory sensorFactory = (SensorFactory) sensorEntry.getFactory();
        // feelingSize 由 ai.feelingSize() 算出传入（尺寸是 ai 层 Domain 知识）
        ISensor sensor = sensorFactory.create(ai.feelingSize());

        // === 查 EffectorRegistry 取下层 effector factory（叶子，无递归）===
        Registry<?> effectorRegistry = RegistryManager.INSTANCE.get(RegistryIds.EFFECTOR);
        RegistryEntry<?> effectorEntry = effectorRegistry.resolve(config.getString(RegistryIds.EFFECTOR.toString()));
        EffectorFactory effectorFactory = (EffectorFactory) effectorEntry.getFactory();
        // behaviorSize 由 ai.behaviorSize() 算出传入（尺寸是 ai 层 Domain 知识）
        IEffector effector = effectorFactory.create(ai.behaviorSize());

        // 三注入构造（ReflexArcSystemAgent 只直接用 ai/sensor/effector，不感知 process/nn/vision/muscle）
        return new ReflexArcSystemAgent(ai, sensor, effector);
    }

    @Override
    public List<ParamOption> getParamOptions() {
        // "允许附身"开关（per-maid 布尔）。getter/setter 复用 AutoTask 的 per-maid 持久化。
        // boolean parse 直接在 lambda 里——太简单，不需要工具方法。
        // tooltip 用 Component.keybind 显示玩家实际绑定的附身键（改键后自动更新），
        // 并说明"附身后才激活 smarter AI"——避免玩家找不到附身方式或误以为开关即激活。
        return List.of(
                ParamOption.of(
                        Component.translatable("option.smarter_touhou_maids.allow_possession"),
                        Component.translatable(
                                "option.smarter_touhou_maids.allow_possession.tooltip",
                                Component.keybind("key.smarter_touhou_maids.possession")),
                        maid -> String.valueOf(AutoTask.isPossessionEnabled(maid)),
                        (maid, text) -> AutoTask.setPossessionEnabled(maid, Boolean.parseBoolean(text.trim())))
                .withControlHint("toggle"));
    }
}
