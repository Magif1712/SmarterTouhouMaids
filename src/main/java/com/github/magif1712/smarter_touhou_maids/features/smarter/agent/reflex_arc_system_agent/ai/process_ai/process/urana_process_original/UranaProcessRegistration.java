package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.ProcessFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.original_bnn.BnnModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.standard_bnn.StandardBnnModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.Registry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryManager;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.ProcessAiRegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.LegacyRegistryIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 旧版 urana_process_original 的<b>自包含注册</b>（仿 {@code BnnRegistration} 附属模组式注册风格）。
 * <p>
 * 旧版 urana 不再在 {@code AiModeDefaults} 里登记（AiModeDefaults 只管 agent 层默认），
 * 而是利用 Forge 的 {@code @Mod.EventBusSubscriber} 自动注册机制，在 {@link FMLCommonSetupEvent}：
 * <ol>
 *   <li>创建 NN_LEGACY registry（旧版 INeuralNetwork 接口与新版不兼容，旧版 nn 迁此独立 registry），
 *       注册 standard_bnn + bnn（original_bnn）两个旧版 nn entry。</li>
 *   <li>向已存在的 ProcessRegistry 追加 urana_original entry（subRegistryId=NN_LEGACY）。</li>
 * </ol>
 * <p>
 * <b>时序</b>：ProcessRegistry 由 AI 层 {@code ProcessAiRegistration}（@EventBusSubscriber, HIGHEST）
 * 创建。本类用 {@link EventPriority#LOWEST} 确保在 AI 层之后运行——ProcessRegistry 已存在时
 * 才追加 urana_original entry。bail-if-null 作安全网（理论不发生：HIGHEST 先于 LOWEST）。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第3条</b>：把"可扩展注册 / 可删换模式"这个不实在约束，实在化为 Forge 事件订阅机制——
 *       未来删除旧版 urana_process_original 包时，只需删本类，上层零改动。</li>
 *   <li><b>第4条</b>：把"零改动注册"这个不实在意图，实在化为 {@code @SubscribeEvent} 方法里的注册代码。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class UranaProcessRegistration {

    private UranaProcessRegistration() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        String modId = SmarterTouhouMaids.MOD_ID;

        // === 创建 NN_LEGACY registry（旧版 nn：standard_bnn + bnn）===
        // 旧版 INeuralNetwork 接口与新版本不兼容（forward/backward 签名、host 数组类型不同），
        // 故旧版 nn 不能与新版本共用 NN registry，迁到独立的 NN_LEGACY registry。
        ResourceLocation nnLegacyDefault = new ResourceLocation(modId, StandardBnnModes.NN_ID);
        Registry<NnFactory> nnLegacyRegistry = new Registry<>(LegacyRegistryIds.NN_LEGACY, nnLegacyDefault);
        nnLegacyRegistry.register(StandardBnnModes.nnEntry(modId));
        nnLegacyRegistry.register(BnnModes.nnEntry(modId));
        RegistryManager.INSTANCE.register(nnLegacyRegistry);

        // === 向已存在的 ProcessRegistry 追加 urana_original entry ===
        // 核心默认（新版 urana）已由 ProcessAiRegistration（AI 层 @EventBusSubscriber）注册，
        // 此处只追加旧版作为可选。subRegistryId=NN_LEGACY：选了 urana_original 后还要选旧版 nn。
        Registry<?> processRegistry = RegistryManager.INSTANCE.get(ProcessAiRegistryIds.PROCESS);
        if (processRegistry == null) {
            // 核心默认尚未注册（理论上不会发生：HIGHEST 先于 LOWEST）。
            return;
        }

        @SuppressWarnings("unchecked")
        Registry<ProcessFactory> typedProcessRegistry = (Registry<ProcessFactory>) processRegistry;

        ResourceLocation uranaOriginalId = new ResourceLocation(modId, "urana_original");
        typedProcessRegistry.register(new RegistryEntry<>(
                uranaOriginalId,
                "mode." + modId + ".process.urana_original",
                new UranaProcessFactory(),
                LegacyRegistryIds.NN_LEGACY));
    }
}
