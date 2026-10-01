package com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.possession.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

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
            com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.possession.ServerPossessionManager.INSTANCE.handlePossessionRequest(sender, msg.maidUUID, msg.start);
        });
        ctx.get().setPacketHandled(true);
    }
}