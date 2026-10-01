package com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.possession.network;

import com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.possession.core.PossessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

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