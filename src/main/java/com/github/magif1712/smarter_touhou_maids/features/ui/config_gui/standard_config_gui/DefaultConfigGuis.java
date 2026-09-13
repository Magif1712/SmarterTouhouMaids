package com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Branch;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.ConceptTree;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Meta;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Node;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.ConfigGuiFactory;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.ConfigGuiIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 主模组默认配置 GUI 注册：在 {@code FMLClientSetupEvent} 调用 {@link #registerDefaults()}。
 * <p>
 * 概念树原生注册：创建 CONFIG_GUI 插槽（GUI 域根，与 agent 域平行的第二根）并注册默认
 * Branch：{@code smarter_touhou_maids:default} → {@code AutoTaskConfigScreen::new}（标准配置界面）。
 * <p>
 * <b>客户端专用</b>（{@code @OnlyIn(Dist.CLIENT)}）：ConfigGuiFactory 返回 Screen（客户端对象），
 * 故本插槽仅在客户端注册——服务端的 ConceptTree 不含 CONFIG_GUI 根（服务端不关心客户端 GUI 渲染）。
 * <p>
 * 附属模组在自己的注册事件监听里向 CONFIG_GUI 插槽挂自己的 Branch，
 * {@link com.github.magif1712.smarter_touhou_maids.features.ui.GuiSelectorScreen} 自动列出。
 */
@OnlyIn(Dist.CLIENT)
public final class DefaultConfigGuis {
    private DefaultConfigGuis() {
    }

    public static void registerDefaults() {
        String modId = SmarterTouhouMaids.MOD_ID;
        ResourceLocation defaultId = new ResourceLocation(modId, "default");

        // === CONFIG_GUI 插槽：叶子层（GUI 选择不递归）===
        Node<ConfigGuiFactory> configGui = ConceptTree.builder().node(ConfigGuiIds.CONFIG_GUI);
        configGui.addBranch(new Branch<>(
                defaultId,
                (ConfigGuiFactory) AutoTaskConfigScreen::new,
                new Meta("gui." + modId + ".config_gui.default", 0, modId)));
        configGui.defaultBranch(defaultId);
    }
}
