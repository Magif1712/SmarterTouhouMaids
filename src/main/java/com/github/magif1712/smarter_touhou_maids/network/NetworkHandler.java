package com.github.magif1712.smarter_touhou_maids.network;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.network.ClientboundMaidDataSyncPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.network.ClientboundPossessionSyncPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.network.ServerboundPossessionRequestPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.network.ServerboundSetPossessionEnabledPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ClientboundAiModeSyncPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ClientboundMinDtMillisSyncPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ClientboundSmarterModeSyncPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ServerboundActionIntentPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ServerboundSetAiModePacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ServerboundBehaviorSyncPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ServerboundSetMinDtMillisPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ServerboundSetSmarterModePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通信处理器
 * <p>
 * 编码铁律:
 * 1. 使用固定的协议版本 "1"。
 * 2. 所有数据包使用固定的、递增的 int 索引注册。
 */
public final class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SmarterTouhouMaids.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void init() {
        int index = 0;
        // 编码铁律: 所有 handle 方法必须在内部使用 enqueueWork 将逻辑提交到主线程。
        INSTANCE.registerMessage(index++, ServerboundPossessionRequestPacket.class, ServerboundPossessionRequestPacket::encode, ServerboundPossessionRequestPacket::decode, ServerboundPossessionRequestPacket::handle);
        INSTANCE.registerMessage(index++, ClientboundPossessionSyncPacket.class, ClientboundPossessionSyncPacket::encode, ClientboundPossessionSyncPacket::decode, ClientboundPossessionSyncPacket::handle);
        INSTANCE.registerMessage(index++, ServerboundSetPossessionEnabledPacket.class, ServerboundSetPossessionEnabledPacket::encode, ServerboundSetPossessionEnabledPacket::decode, ServerboundSetPossessionEnabledPacket::handle);
        INSTANCE.registerMessage(index++, ClientboundMaidDataSyncPacket.class, ClientboundMaidDataSyncPacket::encode, ClientboundMaidDataSyncPacket::decode, ClientboundMaidDataSyncPacket::handle);

        INSTANCE.registerMessage(index++, ServerboundSetSmarterModePacket.class,
                ServerboundSetSmarterModePacket::encode,
                ServerboundSetSmarterModePacket::decode,
                ServerboundSetSmarterModePacket::handle);

        INSTANCE.registerMessage(index++, ClientboundSmarterModeSyncPacket.class,
                ClientboundSmarterModeSyncPacket::encode,
                ClientboundSmarterModeSyncPacket::decode,
                ClientboundSmarterModeSyncPacket::handle);

        INSTANCE.registerMessage(index++, ServerboundBehaviorSyncPacket.class,
                ServerboundBehaviorSyncPacket::encode,
                ServerboundBehaviorSyncPacket::decode,
                ServerboundBehaviorSyncPacket::handle);

        INSTANCE.registerMessage(index++, ServerboundSetMinDtMillisPacket.class,
                ServerboundSetMinDtMillisPacket::encode,
                ServerboundSetMinDtMillisPacket::decode,
                ServerboundSetMinDtMillisPacket::handle);

        INSTANCE.registerMessage(index++, ClientboundMinDtMillisSyncPacket.class,
                ClientboundMinDtMillisSyncPacket::encode,
                ClientboundMinDtMillisSyncPacket::decode,
                ClientboundMinDtMillisSyncPacket::handle);

        // 效应器：客户端 → 服务端 发送解码后的操作要求
        INSTANCE.registerMessage(index++, ServerboundActionIntentPacket.class,
                ServerboundActionIntentPacket::encode,
                ServerboundActionIntentPacket::decode,
                ServerboundActionIntentPacket::handle);

        // AI 模式选择：客户端 → 服务端 设置某层 registry 的选中 entry
        INSTANCE.registerMessage(index++, ServerboundSetAiModePacket.class,
                ServerboundSetAiModePacket::encode,
                ServerboundSetAiModePacket::decode,
                ServerboundSetAiModePacket::handle);

        // AI 模式选择：服务端 → 客户端 同步确认
        INSTANCE.registerMessage(index++, ClientboundAiModeSyncPacket.class,
                ClientboundAiModeSyncPacket::encode,
                ClientboundAiModeSyncPacket::decode,
                ClientboundAiModeSyncPacket::handle);
    }

    private NetworkHandler() {
    }
}