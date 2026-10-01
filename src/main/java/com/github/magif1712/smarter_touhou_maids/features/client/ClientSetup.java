package com.github.magif1712.smarter_touhou_maids.features.client;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.maid.menu.InitMenus;
import com.github.magif1712.smarter_touhou_maids.features.ui.GuiSelectorScreen;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.DefaultConfigGuis;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.DefaultPanels;
import com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.possession.client.setup.KeysRegistry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.possession.core.PossessionManager;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
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

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeysRegistry.POSSESSION_KEY);
    }
}