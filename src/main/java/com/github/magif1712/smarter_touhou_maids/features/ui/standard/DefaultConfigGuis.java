package com.github.magif1712.smarter_touhou_maids.features.ui.standard;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.Registry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryManager;
import com.github.magif1712.smarter_touhou_maids.features.ui.ConfigGuiFactory;
import com.github.magif1712.smarter_touhou_maids.features.ui.ConfigGuiIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 主模组默认配置 GUI 注册：在 {@code FMLClientSetupEvent} 调用 {@link #registerDefaults()}。
 * <p>
 * 创建 {@code Registry<ConfigGuiFactory>}（id={@link com.github.magif1712.smarter_touhou_maids.features.ui.ConfigGuiIds#CONFIG_GUI}）并注册默认 entry：
 * {@code smarter_touhou_maids:default} → {@code AutoTaskConfigScreen::new}（标准配置界面）。
 * <p>
 * <b>本类在 standard/ 而非 ui/ 顶层</b>（真善美第2条：顶层不依赖具体实现）：
 * 本类引用 {@link AutoTaskConfigScreen}（standard/ 的具体实现），故属于实现包而非接口包。
 * 类比 {@code AiModeDefaults} 引用 {@code BnnNnFactory} 但放在 {@code registry/} 而非 {@code nn/}——
 * 注册代码引用具体实现，但注册代码本身不在接口包内。
 * 这样删掉 standard/ 后，顶层 ui/（{@link ConfigGuiFactory} +
 * {@link com.github.magif1712.smarter_touhou_maids.features.ui.GuiSelectorScreen GuiSelectorScreen} +
 * {@link com.github.magif1712.smarter_touhou_maids.features.ui.GuiSelectionStore GuiSelectionStore}）
 * 仍可独立编译——满足"单一具体实现子包可独立运行"。
 * <p>
 * <b>客户端专用</b>（{@code @OnlyIn(Dist.CLIENT)}）：ConfigGuiFactory 返回 Screen（客户端对象），
 * 故 registry + entries 仅在客户端创建。服务端的 RegistryManager 不含 CONFIG_GUI——服务端不关心客户端 GUI 渲染。
 * <p>
 * 附属模组在自己的 {@code FMLClientSetupEvent} 调
 * {@code RegistryManager.INSTANCE.get(CONFIG_GUI).register(new RegistryEntry<>(myId, myI18nKey, myFactory, null))}
 * 即可追加自定义 GUI，{@link com.github.magif1712.smarter_touhou_maids.features.ui.GuiSelectorScreen} 自动列出。
 * <p>
 * 设计原则（真善美第3条）：把"主模组提供哪些 GUI"这个不实在的约束，实在化为注册代码。
 *
 * @see com.github.magif1712.smarter_touhou_maids.features.ui.ConfigGuiFactory
 * @see com.github.magif1712.smarter_touhou_maids.features.ui.GuiSelectorScreen
 */
@OnlyIn(Dist.CLIENT)
public final class DefaultConfigGuis {
    private DefaultConfigGuis() {
    }

    public static void registerDefaults() {
        String modId = SmarterTouhouMaids.MOD_ID;
        ResourceLocation defaultId = new ResourceLocation(modId, "default");

        // === ConfigGuiRegistry：叶子层，subRegistryId=null（GUI 选择不递归） ===
        Registry<ConfigGuiFactory> registry = new Registry<>(ConfigGuiIds.CONFIG_GUI, defaultId);
        registry.register(new RegistryEntry<>(
                defaultId,
                "gui." + modId + ".config_gui.default",
                AutoTaskConfigScreen::new,
                null)); // 叶子，无下层
        RegistryManager.INSTANCE.register(registry);
    }
}
