package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.AgentFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ReflexArcSystemAgentFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.AiFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.ProcessAiFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.ProcessFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.UranaProcessFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.BnnNnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.SensorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.PossessionSensorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.EffectorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.bionic_muscle_effector.BionicMuscleEffectorFactory;
import net.minecraft.resources.ResourceLocation;

/**
 * 主模组默认模式注册：在 FMLCommonSetupEvent 调用 {@link #registerDefaults()}。
 * <p>
 * 注册三个 {@link Registry} 到 {@link RegistryManager}，每个 registry 注册一个默认 entry：
 * <ul>
 *   <li>NnRegistry（id={@link RegistryIds#NN}）：bnn → {@link BnnNnFactory}，subRegistryId=null（叶子）</li>
 *   <li>ProcessRegistry（id={@link RegistryIds#PROCESS}）：urana → {@link UranaProcessFactory}，
 *       subRegistryId={@link RegistryIds#NN}（选了 urana 后还要选 nn）</li>
 *   <li>AiRegistry（id={@link RegistryIds#AI}）：process_ai → {@link ProcessAiFactory}，
 *       subRegistryId={@link RegistryIds#PROCESS}（选了流程型 ai 后还要选 process）</li>
 * </ul>
 * <p>
 * 附属模组在自己的 FMLCommonSetupEvent 里调用 {@link RegistryManager#register(Registry)}
 * 注册自己的 registry，或在已有 registry 里 {@link Registry#register(RegistryEntry)} 追加自己的 entry。
 * <p>
 * 设计原则（真善美第3条）：把"主模组提供哪些模式"这个不实在的约束，实在化为注册代码。
 */
public final class AiModeDefaults {
    private AiModeDefaults() {
    }

    public static void registerDefaults() {
        String modId = SmarterTouhouMaids.MOD_ID;

        ResourceLocation agentDefault = new ResourceLocation(modId, "smarter");
        ResourceLocation nnDefault = new ResourceLocation(modId, "bnn");
        ResourceLocation processDefault = new ResourceLocation(modId, "urana");
        ResourceLocation aiDefault = new ResourceLocation(modId, "process_ai");
        ResourceLocation sensorDefault = new ResourceLocation(modId, "possession_sensor");
        ResourceLocation effectorDefault = new ResourceLocation(modId, "bionic_muscle_effector");

        // === NnRegistry：叶子层，subRegistryId=null ===
        Registry<NnFactory> nnRegistry = new Registry<>(RegistryIds.NN, nnDefault);
        nnRegistry.register(new RegistryEntry<>(
                nnDefault,
                "mode." + modId + ".nn.bnn",
                new BnnNnFactory(),
                null)); // 叶子，无下层
        RegistryManager.INSTANCE.register(nnRegistry);

        // === ProcessRegistry：subRegistryId=NN（选了 process 后还要选 nn）===
        Registry<ProcessFactory> processRegistry = new Registry<>(RegistryIds.PROCESS, processDefault);
        processRegistry.register(new RegistryEntry<>(
                processDefault,
                "mode." + modId + ".process.urana",
                new UranaProcessFactory(),
                RegistryIds.NN));
        RegistryManager.INSTANCE.register(processRegistry);

        // === AiRegistry：subRegistryId=PROCESS（选了 ai 后还要选 process）===
        Registry<AiFactory> aiRegistry = new Registry<>(RegistryIds.AI, aiDefault);
        aiRegistry.register(new RegistryEntry<>(
                aiDefault,
                "mode." + modId + ".ai.process_ai",
                new ProcessAiFactory(),
                RegistryIds.PROCESS));
        RegistryManager.INSTANCE.register(aiRegistry);

        // === AgentRegistry：顶层，subRegistryId=AI（选了 agent 后还要选 ai）===
        Registry<AgentFactory> agentRegistry = new Registry<>(RegistryIds.AGENT, agentDefault);
        agentRegistry.register(new RegistryEntry<>(
                agentDefault,
                "mode." + modId + ".agent.smarter",
                new ReflexArcSystemAgentFactory(),
                RegistryIds.AI));
        RegistryManager.INSTANCE.register(agentRegistry);

        // === SensorRegistry：叶子层，subRegistryId=null（与 ai 并列，agent 下 sensor+ai+effector）===
        // 感受器是组装链叶子（sensor 之下无选择），故独立非递归。default=possession_sensor。
        Registry<SensorFactory> sensorRegistry = new Registry<>(RegistryIds.SENSOR, sensorDefault);
        sensorRegistry.register(new RegistryEntry<>(
                sensorDefault,
                "mode." + modId + ".sensor.possession_sensor",
                new PossessionSensorFactory(),
                null)); // 叶子，无下层
        RegistryManager.INSTANCE.register(sensorRegistry);

        // === EffectorRegistry：叶子层，subRegistryId=null（与 ai 并列，agent 下 sensor+ai+effector）===
        // 效应器是组装链叶子，故独立非递归。default=bionic_muscle_effector。
        Registry<EffectorFactory> effectorRegistry = new Registry<>(RegistryIds.EFFECTOR, effectorDefault);
        effectorRegistry.register(new RegistryEntry<>(
                effectorDefault,
                "mode." + modId + ".effector.bionic_muscle_effector",
                new BionicMuscleEffectorFactory(),
                null)); // 叶子，无下层
        RegistryManager.INSTANCE.register(effectorRegistry);
    }
}
