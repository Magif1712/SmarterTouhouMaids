package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.sensor.possession_sensor.possession.network;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.sensor.possession_sensor.possession.ServerPossessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 数据包: 客户端 -> 服务器
 * 用途: 请求开始或停止附身
 * <p>
 * 编码铁律:
 * 1. handle 方法必须使用 enqueueWork 将逻辑提交到主线程。
 */
public class ServerboundPossessionRequestPacket {
    private final UUID maidUUID;
    private final boolean start;

    public ServerboundPossessionRequestPacket(UUID maidUUID, boolean start) {
        this.maidUUID = maidUUID;
        this.start = start;
    }

    public static void encode(ServerboundPossessionRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUUID);
        buf.writeBoolean(msg.start);
    }

    public static ServerboundPossessionRequestPacket decode(FriendlyByteBuf buf) {
        return new ServerboundPossessionRequestPacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(ServerboundPossessionRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) {
                return;
            }
            ServerPossessionManager.INSTANCE.handlePossessionRequest(sender, msg.maidUUID, msg.start);
        });
        ctx.get().setPacketHandled(true);
    }
}