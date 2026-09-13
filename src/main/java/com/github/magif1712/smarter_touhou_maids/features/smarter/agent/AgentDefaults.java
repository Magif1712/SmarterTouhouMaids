package com.github.magif1712.smarter_touhou_maids.features.smarter.agent;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.AgentFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.AgentNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.IAgent;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ReflexArcSystemAgentFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.EffectorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.bionic_muscle_effector.BionicMuscleEffectorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.SensorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.PossessionSensorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Branch;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.ConceptTree;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Meta;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Node;
import net.minecraft.resources.ResourceLocation;

/**
 * 主模组 agent 层默认模式注册：在 FMLCommonSetupEvent 调用 {@link #registerDefaults()}。
 * <p>
 * <b>概念树原生注册</b>（定理1(4)：AGENT 插槽在上层包含 SENSOR/AI/EFFECTOR 同层三插槽）：
 * <ul>
 *   <li>AGENT 插槽：单分支 smarter（合并裁决：新旧代理合一，新版实现经载体契约兼容旧链），
 *       声明 children "ai"/"sensor"/"effector"。</li>
 *   <li>SENSOR 插槽：possession_sensor_original（旧，推模型契约）+ on_demand（新，拉模型契约，默认）。</li>
 *   <li>EFFECTOR 插槽：bionic_muscle_effector（唯一，默认）。</li>
 * </ul>
 * AI 层（process_ai）与 process 层（urana/mapper/nn）由各层自己的 @EventBusSubscriber 自注册。
 * <p>
 * 附属模组在自己的注册事件监听里经 {@code ConceptTree.builder()} 挂自己的 Node/Branch——
 * 主模组不 import 附属类、不硬编码附属 modid。
 */
public final class AgentDefaults {
    private AgentDefaults() {
    }

    public static void registerDefaults() {
        String modId = SmarterTouhouMaids.MOD_ID;

        ResourceLocation sensorOnDemand = PossessionSensorFactory.SENSOR_ID;
        ResourceLocation sensorLegacy = new ResourceLocation(modId, "possession_sensor");
        ResourceLocation effectorBionic = new ResourceLocation(modId, "bionic_muscle_effector");

        // === AGENT 插槽（单分支）===
        Node<IAgent> agent = ConceptTree.builder().node(AgentNodeKeys.AGENT);
        Branch<IAgent> smarter = new Branch<>(
                ReflexArcSystemAgentFactory.AGENT_ID,
                new ReflexArcSystemAgentFactory(),
                new Meta("mode." + modId + ".agent.smarter", 0, modId));
        smarter.addChild("ai", AgentNodeKeys.AI);
        smarter.addChild("sensor", AgentNodeKeys.SENSOR);
        smarter.addChild("effector", AgentNodeKeys.EFFECTOR);
        agent.addBranch(smarter);
        agent.defaultBranch(ReflexArcSystemAgentFactory.AGENT_ID);

        // === SENSOR 插槽（旧推模型 / 新拉模型，默认新）===
        Node<SensorFactory> sensor = ConceptTree.builder().node(AgentNodeKeys.SENSOR);
        sensor.addBranch(new Branch<>(
                sensorLegacy,
                new com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor_original.PossessionSensorFactory(),
                new Meta("mode." + modId + ".sensor.possession_sensor", 0, modId)));
        sensor.addBranch(new Branch<>(
                sensorOnDemand,
                new PossessionSensorFactory(),
                new Meta("mode." + modId + ".sensor.on_demand_possession_sensor", 0, modId)));
        sensor.defaultBranch(sensorOnDemand);

        // === EFFECTOR 插槽（单分支）===
        Node<EffectorFactory> effector = ConceptTree.builder().node(AgentNodeKeys.EFFECTOR);
        effector.addBranch(new Branch<>(
                effectorBionic,
                new BionicMuscleEffectorFactory(),
                new Meta("mode." + modId + ".effector.bionic_muscle_effector", 0, modId)));
        effector.defaultBranch(effectorBionic);
    }
}
