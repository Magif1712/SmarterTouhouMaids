package com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.possession.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ServerboundSetPossessionEnabledPacket {
    private final UUID maidUUID;
    private final boolean enabled;

    public ServerboundSetPossessionEnabledPacket(UUID maidUUID, boolean enabled) {
        this.maidUUID = maidUUID;
        this.enabled = enabled;
    }

    public static void encode(ServerboundSetPossessionEnabledPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUUID);
        buf.writeBoolean(msg.enabled);
    }

    public static ServerboundSetPossessionEnabledPacket decode(FriendlyByteBuf buf) {
        return new ServerboundSetPossessionEnabledPacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(ServerboundSetPossessionEnabledPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) {
                return;
            }
            com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.possession.ServerPossessionManager.INSTANCE.setPossessionEnabled(sender, msg.maidUUID, msg.enabled);
        });
        ctx.get().setPacketHandled(true);
    }
}