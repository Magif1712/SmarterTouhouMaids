package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.AgentNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.ProcessAiNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.IProcessSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.standard_bnn.StandardBnnModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.IPushEncodedSensor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Branch;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.ConceptTree;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Meta;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Node;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 旧版 urana_process_original 的<b>自包含注册</b>（仿附属模组式注册风格）。
 * <p>
 * 在 {@link FMLCommonSetupEvent} 中（概念树原生注册）：
 * <ol>
 *   <li>创建 NN_LEGACY 插槽（旧版 INeuralNetwork 接口与新版不兼容，旧版 nn 在此独立插槽），
 *       注册 standard_bnn（默认）。bnn（original_bnn）由 {@code BnnRegistration} 以附属样板方式追加。</li>
 *   <li>向已存在的 PROCESS 插槽追加 urana_original Branch（child "nn" → NN_LEGACY，
 *       契约：只兼容推模型感受器 {@code requires(SENSOR, IPushEncodedSensor)}）。</li>
 * </ol>
 * <p>
 * <b>时序</b>：PROCESS 插槽由 AI 层 {@code ProcessAiRegistration}（HIGHEST）创建。
 * 本类用 {@link EventPriority#LOWEST} 确保在 AI 层之后运行。
 * <p>
 * 设计原则（真善美第3条）：把"可扩展注册 / 可删换模式"这个不实在约束，实在化为 Forge 事件订阅机制——
 * 未来删除旧版 urana_process_original 包时，只需删本包，上层零改动。
 */
@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class UranaProcessRegistration {

    private UranaProcessRegistration() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        String modId = SmarterTouhouMaids.MOD_ID;

        // === NN_LEGACY 插槽（旧版 nn：standard_bnn 默认；bnn 由 BnnRegistration 追加）===
        Node<NnFactory> nnLegacy = ConceptTree.builder().node(LegacyNodeKeys.NN_LEGACY);
        nnLegacy.addBranch(StandardBnnModes.nnBranch(modId));
        nnLegacy.defaultBranch(new ResourceLocation(modId, StandardBnnModes.NN_ID));

        // === 向 PROCESS 插槽追加 urana_original 分支 ===
        // child "nn" → NN_LEGACY（旧版 nn 插槽）；契约：BoolVector 位平面推模型链
        // → 只兼容推模型感受器（推论2 的类型校验表达）。
        Node<IProcessSystem> process = ConceptTree.builder().node(ProcessAiNodeKeys.PROCESS);
        Branch<IProcessSystem> uranaOriginal = new Branch<>(
                new ResourceLocation(modId, "urana_original"),
                new UranaProcessFactory(),
                new Meta("mode." + modId + ".process.urana_original", 0, modId));
        uranaOriginal.addChild("nn", LegacyNodeKeys.NN_LEGACY);
        uranaOriginal.addRequirement(AgentNodeKeys.SENSOR.id(), IPushEncodedSensor.class);
        process.addBranch(uranaOriginal);
    }
}
