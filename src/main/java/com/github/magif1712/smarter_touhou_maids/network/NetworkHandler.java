package com.github.magif1712.smarter_touhou_maids.network;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.AgentNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ClientboundAiModeSyncPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ClientboundParamSyncPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ClientboundSmarterModeSyncPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ServerboundSetAiModePacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ServerboundSetParamPacket;
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
 * 3. 各代理分支的网络包经各包自己的 {@code AgentNetwork.registerPackets} 分段注册
 *    （possession/effector 包是分支私有模式；同版本 client/server 索引一致即可）。
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
        // === 代理分支索引段（各包自带贡献，依次分配）===
        int index = 0;
        // 新代理（reflex_arc_system_agent）：4 个 possession 包 + ActionIntent（索引 0-4）
        index = AgentNetwork.registerPackets(INSTANCE, index);
        // 原初代理（reflex_arc_system_agent_original）：位平面链同构包（索引 5-9）
        index = com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.AgentNetwork.registerPackets(INSTANCE, index);

        // === smarter 通用包（与代理无关）===
        INSTANCE.registerMessage(index++, ServerboundSetSmarterModePacket.class,
                ServerboundSetSmarterModePacket::encode,
                ServerboundSetSmarterModePacket::decode,
                ServerboundSetSmarterModePacket::handle);

        INSTANCE.registerMessage(index++, ClientboundSmarterModeSyncPacket.class,
                ClientboundSmarterModeSyncPacket::encode,
                ClientboundSmarterModeSyncPacket::decode,
                ClientboundSmarterModeSyncPacket::handle);

        // 通用 per-maid 参数：客户端 → 服务端 设置某参数值（nbtKey → longValue）
        INSTANCE.registerMessage(index++, ServerboundSetParamPacket.class,
                ServerboundSetParamPacket::encode,
                ServerboundSetParamPacket::decode,
                ServerboundSetParamPacket::handle);

        // 通用 per-maid 参数：服务端 → 客户端 同步确认
        INSTANCE.registerMessage(index++, ClientboundParamSyncPacket.class,
                ClientboundParamSyncPacket::encode,
                ClientboundParamSyncPacket::decode,
                ClientboundParamSyncPacket::handle);

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
