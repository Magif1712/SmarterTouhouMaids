package com.github.magif1712.smarter_touhou_maids.features.ui;

import com.github.magif1712.smarter_touhou_maids.features.maid.menu.AutoTaskConfigMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 配置 GUI 工厂：按共享的 {@link AutoTaskConfigMenu} 创建一个配置界面 Screen 实例。
 * <p>
 * <b>是 Factory 而非 instance</b>（与 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.AgentFactory}
 * 同构，与 {@link IConfigPanel} "无 Factory" 不同）：
 * Screen 构造需 {@code (menu, inventory, title)} 三参数注入——这是"有下层注入组装"场景，
 * Factory 在 C 中是必要的，不是冗余抽象。附属直接注册实例（{@code PanelRegistry.register}）
 * 只适用于无状态视图；Screen 需要参数注入，故用 Factory。
 * <p>
 * <b>签名与 {@code AutoTaskConfigScreen} 构造函数同构</b>：{@code AutoTaskConfigScreen::new}
 * 即一个 ConfigGuiFactory（协变返回类型：AutoTaskConfigScreen → AbstractContainerScreen&lt;AutoTaskConfigMenu&gt;）。
 * <p>
 * 设计原则（真善美第3条）：把"配置 GUI 可选"这个不实在的约束，实在化为有签名的 Factory 接口。
 *
 * @see com.github.magif1712.smarter_touhou_maids.features.ui.standard.DefaultConfigGuis
 */
@OnlyIn(Dist.CLIENT)
@FunctionalInterface
public interface ConfigGuiFactory {
    /**
     * @param menu      共享的 {@link AutoTaskConfigMenu}（maid 信息源），由 MenuProvider 构造、Minecraft 持有
     * @param inventory 玩家背包
     * @param title     标题
     * @return 创建好的 Screen 实例（尚未 init——init 由 Minecraft 在 setScreen 后调用）
     */
    AbstractContainerScreen<AutoTaskConfigMenu> create(AutoTaskConfigMenu menu, Inventory inventory, Component title);
}
