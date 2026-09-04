package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.sensor.possession_sensor.possession.network;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.sensor.possession_sensor.possession.core.PossessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 数据包: 服务器 -> 客户端
 * 用途: NBT修改的回执，用于同步客户端UI状态
 * <p>
 * 编码铁律:
 * 1. handle 方法必须使用 enqueueWork 将逻辑提交到主线程。
 */
public class ClientboundMaidDataSyncPacket {
    private final UUID maidUUID;
    private final boolean enabled;

    public ClientboundMaidDataSyncPacket(UUID maidUUID, boolean enabled) {
        this.maidUUID = maidUUID;
        this.enabled = enabled;
    }

    public static void encode(ClientboundMaidDataSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUUID);
        buf.writeBoolean(msg.enabled);
    }

    public static ClientboundMaidDataSyncPacket decode(FriendlyByteBuf buf) {
        return new ClientboundMaidDataSyncPacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(ClientboundMaidDataSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PossessionManager.INSTANCE.onMaidDataSync(msg.maidUUID, msg.enabled));
        });
        ctx.get().setPacketHandled(true);
    }
}