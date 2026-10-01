package com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.possession.network;

import com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.possession.core.PossessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

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