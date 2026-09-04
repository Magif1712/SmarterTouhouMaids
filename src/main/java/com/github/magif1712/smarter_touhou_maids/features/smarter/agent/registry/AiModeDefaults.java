package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.AgentFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ReflexArcSystemAgentFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.SensorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.PossessionSensorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.EffectorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.bionic_muscle_effector.BionicMuscleEffectorFactory;
import net.minecraft.resources.ResourceLocation;

/**
 * 主模组 agent 层默认模式注册：在 FMLCommonSetupEvent 调用 {@link #registerDefaults()}。
 * <p>
 * <b>agent 层只注册自身 + 与 ai 并列的叶子层</b>（真善美第2条：每层只决定其下一层）：
 * <ul>
 *   <li>AgentRegistry（id={@link RegistryIds#AGENT}）：smarter → 新代理
 *       {@link ReflexArcSystemAgentFactory}（默认，正统延续），smarter_original → 原初代理
 *       工厂（临时兼容层，随时可删）。subRegistryId={@link RegistryIds#AI}（选了 agent 后还要选 ai）。</li>
 *   <li>SensorRegistry（id={@link RegistryIds#SENSOR}）：possession_sensor → 原初代理融合版
 *       （采集+编码合一的推模型，registry 默认，服务原初代理及其旧存档回退）；
 *       on_demand_possession_sensor → 新代理按需编码版（采集/解码分离的拉模型，解码器由
 *       ai 链的 nn 家族贡献、agent 注入——smarter 代理工厂的 sensor 默认）。叶子（subRegistryId=null）。</li>
 *   <li>EffectorRegistry（id={@link RegistryIds#EFFECTOR}）：bionic_muscle_effector → 新代理
 *       肌肉工厂（默认），bionic_muscle_effector_original → 原初代理副本。叶子（subRegistryId=null）。</li>
 * </ul>
 * <p>
 * AI 层（AiRegistry/ProcessRegistry）、Process 层（MapperRegistry/NnRegistry）、旧版层
 * （NnLegacyRegistry/urana_original）由各层自己的 @EventBusSubscriber 自注册：
 * <ul>
 *   <li>{@code ProcessAiRegistration}（@EventBusSubscriber, HIGHEST）：AiRegistry + ProcessRegistry</li>
 *   <li>{@code urana_process.UranaProcessRegistration}（@EventBusSubscriber）：MapperRegistry + NnRegistry</li>
 *   <li>{@code urana_process_original.UranaProcessRegistration}（@EventBusSubscriber, LOWEST）：NnLegacyRegistry + urana_original</li>
 * </ul>
 * <p>
 * 附属模组在自己的 FMLCommonSetupEvent 里调用 {@link RegistryManager#register(Registry)}
 * 注册自己的 registry，或在已有 registry 里 {@link Registry#register(RegistryEntry)} 追加自己的 entry。
 * <p>
 * 设计原则（真善美第2条）：agent 层只定义 AGENT/AI/SENSOR/EFFECTOR（自身 + 直接下层），
 * 不定义 PROCESS/MAPPER/NN（更下层由各层自己定义）。
 */
public final class AiModeDefaults {
    private AiModeDefaults() {
    }

    public static void registerDefaults() {
        String modId = SmarterTouhouMaids.MOD_ID;

        // === AgentRegistry：顶层，subRegistryId=AI（选了 agent 后还要选 ai）===
        Registry<AgentFactory> agentRegistry = new Registry<>(RegistryIds.AGENT, ReflexArcSystemAgentFactory.AGENT_ID);
        // smarter（默认，正统延续）→ 新代理工厂
        // subRegistryId=AI_SMARTER（新版代理独立 AI registry，只含 urana——与原初代理隔离，
        // 避免跨代理流程选择导致的不兼容组合）
        agentRegistry.register(new RegistryEntry<>(
                ReflexArcSystemAgentFactory.AGENT_ID,
                "mode." + modId + ".agent.smarter",
                new ReflexArcSystemAgentFactory(),
                RegistryIds.AI_SMARTER));
        // smarter_original → 原初代理工厂（临时兼容层：旧 BNN 位平面链，随时可删）
        agentRegistry.register(new RegistryEntry<>(
                com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ReflexArcSystemAgentFactory.AGENT_ID,
                "mode." + modId + ".agent.smarter_original",
                new com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ReflexArcSystemAgentFactory(),
                RegistryIds.AI));
        RegistryManager.INSTANCE.register(agentRegistry);

        // === SensorRegistry：叶子层，subRegistryId=null（与 ai 并列，agent 下 sensor+ai+effector）===
        // possession_sensor（registry 默认）→ 原初代理融合版（采集+编码合一的推模型，服务原初代理
        // 及其旧存档回退）。原名保留。
        ResourceLocation sensorDefault = new ResourceLocation(modId, "possession_sensor");
        Registry<SensorFactory> sensorRegistry = new Registry<>(RegistryIds.SENSOR, sensorDefault);
        sensorRegistry.register(new RegistryEntry<>(
                sensorDefault,
                "mode." + modId + ".sensor.possession_sensor",
                new com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.sensor.possession_sensor.PossessionSensorFactory(),
                null)); // 叶子，无下层
        // on_demand_possession_sensor → 新代理按需编码版（采集/解码分离：每帧快照 + AI 消费挂起的
        // 按需解码；解码器由 ai 链的 nn 家族贡献、agent 注入，与感觉载体天然配对）。
        // smarter 代理工厂的 sensor 默认（config 未显式选择时的回退）。
        sensorRegistry.register(new RegistryEntry<>(
                PossessionSensorFactory.SENSOR_ID,
                "mode." + modId + ".sensor.on_demand_possession_sensor",
                new PossessionSensorFactory(),
                null)); // 叶子，无下层
        RegistryManager.INSTANCE.register(sensorRegistry);

        // === EffectorRegistry：叶子层，subRegistryId=null（与 ai 并列）===
        // bionic_muscle_effector（默认）→ 新代理肌肉工厂
        ResourceLocation effectorDefault = new ResourceLocation(modId, "bionic_muscle_effector");
        Registry<EffectorFactory> effectorRegistry = new Registry<>(RegistryIds.EFFECTOR, effectorDefault);
        effectorRegistry.register(new RegistryEntry<>(
                effectorDefault,
                "mode." + modId + ".effector.bionic_muscle_effector",
                new BionicMuscleEffectorFactory(),
                null)); // 叶子，无下层
        // bionic_muscle_effector_original → 原初代理肌肉工厂副本
        ResourceLocation effectorOriginal = new ResourceLocation(modId, "bionic_muscle_effector_original");
        effectorRegistry.register(new RegistryEntry<>(
                effectorOriginal,
                "mode." + modId + ".effector.bionic_muscle_effector_original",
                new com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.effector.bionic_muscle_effector.BionicMuscleEffectorFactory(),
                null)); // 叶子，无下层
        RegistryManager.INSTANCE.register(effectorRegistry);
    }
}
