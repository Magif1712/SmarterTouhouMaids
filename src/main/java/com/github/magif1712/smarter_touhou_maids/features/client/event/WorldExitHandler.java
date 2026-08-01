package com.github.magif1712.smarter_touhou_maids.features.client.event;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 监听世界退出事件，负责清理原生层持有的全局资源。
 */
@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldExitHandler {

    @SubscribeEvent
    public static void onPlayerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // 玩家退出世界/断开连接时释放共享缓冲区
        // NativeLibLoader.releaseSharedNeuronBuffer();
    }
}