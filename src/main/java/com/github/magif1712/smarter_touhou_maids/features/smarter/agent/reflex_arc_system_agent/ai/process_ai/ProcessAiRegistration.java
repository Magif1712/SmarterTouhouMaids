package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.AiFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.ProcessFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.UranaProcessModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.Registry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * AI 层（process_ai）的<b>自包含注册</b>（@EventBusSubscriber）。
 * <p>
 * AI 层自身的 registry id（{@link RegistryIds#AI}）由上层 agent 层定义——
 * 父层决定子层 id，子层引用父层定义（上→下决定，下→上引用）。
 * AI 层决定的直接下层 id（{@link ProcessAiRegistryIds#PROCESS}）在本层定义。
 * <p>
 * 在 {@link FMLCommonSetupEvent} 中：
 * <ol>
 *   <li>创建 AiRegistry（id={@link RegistryIds#AI}），注册 process_ai entry
 *       （subRegistryId={@link ProcessAiRegistryIds#PROCESS}）。</li>
 *   <li>创建 ProcessRegistry（id={@link ProcessAiRegistryIds#PROCESS}），注册 urana entry
 *       （经 {@link UranaProcessModes#processEntry(String)} 自包含贡献，subRegistryId=MAPPER）。</li>
 * </ol>
 * <p>
 * <b>时序</b>：{@code AiModeDefaults.registerDefaults()} 经 {@code modEventBus.addListener}
 * 注册（在 mod 构造器期挂载），先于所有 @EventBusSubscriber 触发，故 AgentRegistry 已存在。
 * 本类用 {@link EventPriority#HIGHEST} 确保在其它 @EventBusSubscriber 之前运行——
 * 旧版 {@code urana_process_original.UranaProcessRegistration}（priority=LOWEST）需要 ProcessRegistry
 * 已存在才能追加 urana_original entry。
 * <p>
 * 设计原则（真善美第2条）：每层只决定其下一层。AI 层只定义 PROCESS（直接下层），
 * 不定义 MAPPER/NN/NN_LEGACY（更下层由各层自己定义）。AI 层引用 agent 层的 AI id（上→下决定）。
 */
@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ProcessAiRegistration {

    private ProcessAiRegistration() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        String modId = SmarterTouhouMaids.MOD_ID;

        // === 创建 AiRegistry（id=AI，由 agent 层定义；AI 层引用父层定义）===
        ResourceLocation aiDefault = new ResourceLocation(modId, "process_ai");
        Registry<AiFactory> aiRegistry = new Registry<>(RegistryIds.AI, aiDefault);
        aiRegistry.register(new RegistryEntry<>(
                aiDefault,
                "mode." + modId + ".ai.process_ai",
                new ProcessAiFactory(),
                ProcessAiRegistryIds.PROCESS)); // 选了流程型 ai 后还要选 process
        RegistryManager.INSTANCE.register(aiRegistry);

        // === 创建 ProcessRegistry（id=PROCESS，由 AI 层定义）===
        // urana（核心默认 process）自包含注册：id/名/factory 由 UranaProcessModes 声明。
        // subRegistryId=MAPPER（由 process 层定义）封装在 UranaProcessModes.processEntry 内。
        ResourceLocation processDefault = new ResourceLocation(modId, UranaProcessModes.PROCESS_ID);
        Registry<ProcessFactory> processRegistry = new Registry<>(ProcessAiRegistryIds.PROCESS, processDefault);
        processRegistry.register(UranaProcessModes.processEntry(modId));
        RegistryManager.INSTANCE.register(processRegistry);
    }
}
