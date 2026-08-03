package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.original_bnn;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.Registry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryManager;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 原初 bnn 的<b>附属模组式自注册</b>（也是附属模组注册新 nn 模式的样板）。
 * <p>
 * 不在 {@code AiModeDefaults} 里登记（AiModeDefaults 只管核心默认 standard_bnn），
 * 而是利用 Forge 的 {@code @Mod.EventBusSubscriber} 自动注册机制，在 {@link FMLCommonSetupEvent}
 * 向已存在的 NnRegistry 追加 original_bnn entry——与真正的附属模组注册路径完全一致。
 * <p>
 * <b>时序</b>：{@code AiModeDefaults.registerDefaults()} 经 {@code modEventBus.addListener} 注册，
 * 在 mod 构造器期挂载；{@code @EventBusSubscriber} 在构造器之后自动挂载，故同一 FMLCommonSetupEvent
 * 上核心默认先于本订阅者触发，NnRegistry 已存在。bail-if-null 作安全网。
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

        Registry<?> nnRegistry = RegistryManager.INSTANCE.get(RegistryIds.NN);
        if (nnRegistry == null) {
            // 核心默认尚未注册（理论上不会发生：addListener 先于 @EventBusSubscriber 触发）。
            return;
        }

        @SuppressWarnings("unchecked")
        Registry<NnFactory> typedRegistry = (Registry<NnFactory>) nnRegistry;

        RegistryEntry<NnFactory> entry = BnnModes.nnEntry(modId);
        typedRegistry.register(entry);
    }
}
