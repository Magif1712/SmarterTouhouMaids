package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.AgentNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.AiFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.IAiSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.IProcessSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.UranaProcessModes;
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
 * AI 层（process_ai）的<b>自包含注册</b>（@EventBusSubscriber）。
 * <p>
 * AI 层自身的插槽键（{@link AgentNodeKeys#AI}）由上层 agent 层定义——
 * 父层决定子层 id，子层引用父层定义（上→下决定，下→上引用）。
 * AI 层决定的直接下层插槽（{@link ProcessAiNodeKeys#PROCESS}）在本层定义。
 * <p>
 * 在 {@link FMLCommonSetupEvent} 中（概念树原生注册）：
 * <ol>
 *   <li>创建 AI 插槽，注册 process_ai Branch（child "process" → PROCESS 插槽）。</li>
 *   <li>创建 PROCESS 插槽，注册 urana Branch（经 {@link UranaProcessModes#processBranch(String)}
 *       自包含贡献，child "mapper" + 拉模型感受器契约），默认指向 urana。</li>
 * </ol>
 * 旧版 urana_original 由旧版包的 {@code UranaProcessRegistration}（LOWEST）追加进 PROCESS 插槽。
 * <p>
 * 设计原则（真善美第2条）：每层只决定其下一层。AI 层只定义 PROCESS（直接下层），
 * 不定义 MAPPER/NN/NN_LEGACY（更下层由各层自己定义）。
 */
@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ProcessAiRegistration {

    private ProcessAiRegistration() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        String modId = SmarterTouhouMaids.MOD_ID;
        ResourceLocation processAiId = new ResourceLocation(modId, "process_ai");

        // === AI 插槽：process_ai 分支（child "process"）===
        Node<IAiSystem> ai = ConceptTree.builder().node(AgentNodeKeys.AI);
        Branch<IAiSystem> processAi = new Branch<>(
                processAiId,
                new ProcessAiFactory(),
                new Meta("mode." + modId + ".ai.process_ai", 0, modId));
        processAi.addChild("process", ProcessAiNodeKeys.PROCESS);
        ai.addBranch(processAi);
        ai.defaultBranch(processAiId);

        // === PROCESS 插槽：urana（默认）+ urana_original（旧版包 LOWEST 追加）===
        Node<IProcessSystem> process = ConceptTree.builder().node(ProcessAiNodeKeys.PROCESS);
        process.addBranch(UranaProcessModes.processBranch(modId));
        process.defaultBranch(new ResourceLocation(modId, UranaProcessModes.PROCESS_ID));
    }
}
