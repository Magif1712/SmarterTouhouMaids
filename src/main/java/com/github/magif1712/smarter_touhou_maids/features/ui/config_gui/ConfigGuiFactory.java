package com.github.magif1712.smarter_touhou_maids.features.ui.config_gui;

import com.github.magif1712.smarter_touhou_maids.features.maid.menu.AutoTaskConfigMenu;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.AssemblyContext;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Factory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.OutSlot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 配置 GUI 工厂：按共享的 {@link AutoTaskConfigMenu} 创建一个配置界面 Screen 实例。
 * <p>
 * <b>是 Factory 而非 instance</b>：Screen 构造需 {@code (menu, inventory, title)} 三参数注入——
 * 这是"有下层注入组装"场景，Factory 在 C 中是必要的，不是冗余抽象。
 * <p>
 * <b>提供者自产</b>：CONFIG_GUI 插槽（GUI 域根，与 agent 域平行的第二根）的分支产物是
 * 本工厂自身——Screen 实例化需要 menu/inventory/title 运行期参数，由 GuiSelectorScreen 带参调用。
 * <p>
 * <b>签名与 {@code AutoTaskConfigScreen} 构造函数同构</b>：{@code AutoTaskConfigScreen::new}
 * 即一个 ConfigGuiFactory（协变返回类型）。
 */
@OnlyIn(Dist.CLIENT)
public interface ConfigGuiFactory extends Factory<ConfigGuiFactory> {
    /**
     * @param menu      共享的 {@link AutoTaskConfigMenu}（maid 信息源），由 MenuProvider 构造、Minecraft 持有
     * @param inventory 玩家背包
     * @param title     标题
     * @return 创建好的 Screen 实例（尚未 init——init 由 Minecraft 在 setScreen 后调用）
     */
    AbstractContainerScreen<AutoTaskConfigMenu> create(AutoTaskConfigMenu menu, Inventory inventory, Component title);

    /** 提供者自产：CONFIG_GUI 插槽的分支产物即本工厂自身。 */
    @Override
    default void create(AssemblyContext ctx, /*->*/ OutSlot<ConfigGuiFactory> out) {
        out.set(this);
    }

    @Override
    default Class<? extends ConfigGuiFactory> producedType() {
        return ConfigGuiFactory.class;
    }
}
