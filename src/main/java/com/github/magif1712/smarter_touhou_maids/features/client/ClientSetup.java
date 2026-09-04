package com.github.magif1712.smarter_touhou_maids.features.client;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.maid.menu.InitMenus;
import com.github.magif1712.smarter_touhou_maids.features.ui.GuiSelectorScreen;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.DefaultConfigGuis;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.DefaultPanels;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.core.PossessionManager;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            DefaultPanels.registerDefaults();
            DefaultConfigGuis.registerDefaults();
            MenuScreens.register(InitMenus.AUTO_TASK_CONFIG_MENU.get(), GuiSelectorScreen::new);
            // 两个代理分支的客户端附身管理器各注册一份（possession 是分支私有模式，D4 形态修正）：
            // 各自 onClientTick 以 maid 的 agent 配置守卫，只有持有附身 maid 的分支执行状态逻辑。
            MinecraftForge.EVENT_BUS.register(PossessionManager.INSTANCE);
            MinecraftForge.EVENT_BUS.register(com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.sensor.possession_sensor.possession.core.PossessionManager.INSTANCE);
        });
    }
}