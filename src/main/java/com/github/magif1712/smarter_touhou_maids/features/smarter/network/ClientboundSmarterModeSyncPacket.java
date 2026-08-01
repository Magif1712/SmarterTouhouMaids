package com.github.magif1712.smarter_touhou_maids.features.smarter.network;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.core.PossessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ClientboundSmarterModeSyncPacket {
    private final UUID maidUUID;
    private final boolean enabled;

    public ClientboundSmarterModeSyncPacket(UUID maidUUID, boolean enabled) {
        this.maidUUID = maidUUID;
        this.enabled = enabled;
    }

    public static void encode(ClientboundSmarterModeSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUUID);
        buf.writeBoolean(msg.enabled);
    }

    public static ClientboundSmarterModeSyncPacket decode(FriendlyByteBuf buf) {
        return new ClientboundSmarterModeSyncPacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(ClientboundSmarterModeSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                PossessionManager.INSTANCE.onSmarterModeSync(msg.maidUUID, msg.enabled));
        });
        ctx.get().setPacketHandled(true);
    }
}