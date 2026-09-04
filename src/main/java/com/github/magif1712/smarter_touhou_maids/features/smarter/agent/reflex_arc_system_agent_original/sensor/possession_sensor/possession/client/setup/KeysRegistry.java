package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.sensor.possession_sensor.possession.client.setup;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class KeysRegistry {
    /**
     * 本分支的附身键。翻译键带 _original 后缀与新代理的附身键区分（多代理共存，D4 形态修正）：
     * 两个独立 KeyMapping 各自 consumeClick，附身请求经 PossessionManager 的分支守卫
     * （目标 maid 的 agent == 本分支才发包）保证只有持有 maid 的分支响应。
     */
    public static final KeyMapping POSSESSION_KEY = new KeyMapping(
            "key.smarter_touhou_maids.possession_original",
            GLFW.GLFW_KEY_P,
            "key.categories.smarter_touhou_maids"
    );

    @SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        event.register(POSSESSION_KEY);
    }
}