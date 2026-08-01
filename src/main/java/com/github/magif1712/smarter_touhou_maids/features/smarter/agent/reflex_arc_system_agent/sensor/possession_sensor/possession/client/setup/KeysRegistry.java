package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.client.setup;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class KeysRegistry {
    public static final KeyMapping POSSESSION_KEY = new KeyMapping(
            "key.smarter_touhou_maids.possession",
            GLFW.GLFW_KEY_P,
            "key.categories.smarter_touhou_maids"
    );

    @SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        event.register(POSSESSION_KEY);
    }
}