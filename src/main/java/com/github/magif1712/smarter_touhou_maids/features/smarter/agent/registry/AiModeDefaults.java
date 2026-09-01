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
 *   <li>AgentRegistry（id={@link RegistryIds#AGENT}）：smarter → {@link ReflexArcSystemAgentFactory}，
 *       subRegistryId={@link RegistryIds#AI}（选了 agent 后还要选 ai）。</li>
 *   <li>SensorRegistry（id={@link RegistryIds#SENSOR}）：possession_sensor，叶子（subRegistryId=null）。</li>
 *   <li>EffectorRegistry（id={@link RegistryIds#EFFECTOR}）：bionic_muscle_effector，叶子（subRegistryId=null）。</li>
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

        ResourceLocation agentDefault = new ResourceLocation(modId, "smarter");
        ResourceLocation sensorDefault = new ResourceLocation(modId, "possession_sensor");
        ResourceLocation effectorDefault = new ResourceLocation(modId, "bionic_muscle_effector");

        // === AgentRegistry：顶层，subRegistryId=AI（选了 agent 后还要选 ai）===
        Registry<AgentFactory> agentRegistry = new Registry<>(RegistryIds.AGENT, agentDefault);
        agentRegistry.register(new RegistryEntry<>(
                agentDefault,
                "mode." + modId + ".agent.smarter",
                new ReflexArcSystemAgentFactory(),
                RegistryIds.AI));
        RegistryManager.INSTANCE.register(agentRegistry);

        // === SensorRegistry：叶子层，subRegistryId=null（与 ai 并列，agent 下 sensor+ai+effector）===
        Registry<SensorFactory> sensorRegistry = new Registry<>(RegistryIds.SENSOR, sensorDefault);
        sensorRegistry.register(new RegistryEntry<>(
                sensorDefault,
                "mode." + modId + ".sensor.possession_sensor",
                new PossessionSensorFactory(),
                null)); // 叶子，无下层
        RegistryManager.INSTANCE.register(sensorRegistry);

        // === EffectorRegistry：叶子层，subRegistryId=null（与 ai 并列）===
        Registry<EffectorFactory> effectorRegistry = new Registry<>(RegistryIds.EFFECTOR, effectorDefault);
        effectorRegistry.register(new RegistryEntry<>(
                effectorDefault,
                "mode." + modId + ".effector.bionic_muscle_effector",
                new BionicMuscleEffectorFactory(),
                null)); // 叶子，无下层
        RegistryManager.INSTANCE.register(effectorRegistry);
    }
}
