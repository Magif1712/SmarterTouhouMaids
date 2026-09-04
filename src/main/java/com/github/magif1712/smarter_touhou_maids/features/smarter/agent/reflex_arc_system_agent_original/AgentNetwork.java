package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.effector.network.ServerboundActionIntentPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.sensor.possession_sensor.possession.network.ClientboundMaidDataSyncPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.sensor.possession_sensor.possession.network.ClientboundPossessionSyncPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.sensor.possession_sensor.possession.network.ServerboundPossessionRequestPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.sensor.possession_sensor.possession.network.ServerboundSetPossessionEnabledPacket;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 原初代理分支向 {@code NetworkHandler} 贡献的网络包注册（索引段分段，与本代理包副本类完全同构）。
 * <p>
 * possession / effector 的网络包是代理分支的私有模式（副本化接线，D4 形态修正）：
 * 各代理包自带 {@link #registerPackets}，{@code NetworkHandler} 依次调用并分配索引段。
 * <p>
 * 原初代理是临时兼容层（随时可删）：删除时移除 {@code NetworkHandler} 中对本类的调用即可。
 */
public final class AgentNetwork {
    private AgentNetwork() {
    }

    /**
     * 注册本分支的 5 个网络包（4 个 possession + 1 个效应器 ActionIntent）。
     *
     * @param channel   共享 SimpleChannel（NetworkHandler 持有）。
     * @param baseIndex 本分支索引段起始值。
     * @return 下一个可用索引（供后续分支接续分配）。
     */
    public static int registerPackets(SimpleChannel channel, int baseIndex) {
        int index = baseIndex;
        // 编码铁律: 所有 handle 方法必须在内部使用 enqueueWork 将逻辑提交到主线程。
        channel.registerMessage(index++, ServerboundPossessionRequestPacket.class, ServerboundPossessionRequestPacket::encode, ServerboundPossessionRequestPacket::decode, ServerboundPossessionRequestPacket::handle);
        channel.registerMessage(index++, ClientboundPossessionSyncPacket.class, ClientboundPossessionSyncPacket::encode, ClientboundPossessionSyncPacket::decode, ClientboundPossessionSyncPacket::handle);
        channel.registerMessage(index++, ServerboundSetPossessionEnabledPacket.class, ServerboundSetPossessionEnabledPacket::encode, ServerboundSetPossessionEnabledPacket::decode, ServerboundSetPossessionEnabledPacket::handle);
        channel.registerMessage(index++, ClientboundMaidDataSyncPacket.class, ClientboundMaidDataSyncPacket::encode, ClientboundMaidDataSyncPacket::decode, ClientboundMaidDataSyncPacket::handle);
        channel.registerMessage(index++, ServerboundActionIntentPacket.class, ServerboundActionIntentPacket::encode, ServerboundActionIntentPacket::decode, ServerboundActionIntentPacket::handle);
        return index;
    }
}
