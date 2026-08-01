package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.network;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.core.PossessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 数据包: 服务器 -> 客户端
 * 用途: 同步玩家的附身状态
 * <p>
 * 编码铁律:
 * 1. handle 方法必须使用 enqueueWork 将逻辑提交到主线程。
 */
public class ClientboundPossessionSyncPacket {
    private final boolean possessing;
    private final UUID maidUUID;

    public ClientboundPossessionSyncPacket(boolean possessing, UUID maidUUID) {
        this.possessing = possessing;
        this.maidUUID = maidUUID;
    }

    public static void encode(ClientboundPossessionSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.possessing);
        buf.writeUUID(msg.maidUUID);
    }

    public static ClientboundPossessionSyncPacket decode(FriendlyByteBuf buf) {
        return new ClientboundPossessionSyncPacket(buf.readBoolean(), buf.readUUID());
    }

    public static void handle(ClientboundPossessionSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PossessionManager.INSTANCE.onServerSync(msg.possessing, msg.maidUUID));
        });
        ctx.get().setPacketHandled(true);
    }
}