package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.original_bnn;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.Registry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryManager;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.LegacyRegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.NnFactory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 原初 bnn 的<b>附属模组式自注册</b>（也是附属模组注册新 nn 模式的样板）。
 * <p>
 * 不在 {@code AiModeDefaults} 里登记（AiModeDefaults 只管新版 cnn），
 * 而是利用 Forge 的 {@code @Mod.EventBusSubscriber} 自动注册机制，在 {@link FMLCommonSetupEvent}
 * 向已存在的 NN_LEGACY registry 追加 original_bnn entry——与真正的附属模组注册路径完全一致。
 * <p>
 * <b>时序</b>：{@code UranaProcessRegistration}（同为 @EventBusSubscriber）创建 NN_LEGACY registry
 * 并注册 standard_bnn + bnn。本类作为 original_bnn 的自包含贡献者，在 NN_LEGACY 已存在时
 * 追加注册（重复覆盖无害）。若本类先于 UranaProcessRegistration 触发则 bail，
 * bnn 仍由 UranaProcessRegistration 兜底注册。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第3条</b>：把"可扩展注册 / 可删换模式"这个不实在约束，实在化为 Forge 事件订阅机制——
 *       新增或删除一个 nn 模式（如本 original_bnn）只需增删一个 {@code @EventBusSubscriber} 类，
 *       AiModeDefaults 与上层零改动，哪怕删除原模式用新模式替换。</li>
 *   <li><b>第4条</b>：把"零改动注册"这个不实在意图，实在化为 {@code @SubscribeEvent} 方法里的注册代码。</li>
 * </ul>
 * <p>
 * <b>附属模组样板</b>：附属模组复制本类结构（改 modid / 改 entry 来源）即可注册自己的 nn 模式，
 * 主模组无需任何改动。
 */
@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BnnRegistration {

    private BnnRegistration() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        String modId = SmarterTouhouMaids.MOD_ID;

        Registry<?> nnLegacyRegistry = RegistryManager.INSTANCE.get(LegacyRegistryIds.NN_LEGACY);
        if (nnLegacyRegistry == null) {
            // NN_LEGACY registry 尚未由 UranaProcessRegistration 创建（@EventBusSubscriber 顺序不保证）。
            // bnn 仍由 UranaProcessRegistration 兜底注册，此处安全退出。
            return;
        }

        @SuppressWarnings("unchecked")
        Registry<NnFactory> typedRegistry = (Registry<NnFactory>) nnLegacyRegistry;

        RegistryEntry<NnFactory> entry = BnnModes.nnEntry(modId);
        typedRegistry.register(entry);
    }
}
