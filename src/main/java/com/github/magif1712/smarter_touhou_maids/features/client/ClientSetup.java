package com.github.magif1712.smarter_touhou_maids.features.client;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.maid.menu.InitMenus;
import com.github.magif1712.smarter_touhou_maids.features.ui.GuiSelectorScreen;
import com.github.magif1712.smarter_touhou_maids.features.ui.standard.DefaultConfigGuis;
import com.github.magif1712.smarter_touhou_maids.features.ui.standard.DefaultPanels;
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
            MinecraftForge.EVENT_BUS.register(PossessionManager.INSTANCE);
        });
    }
}