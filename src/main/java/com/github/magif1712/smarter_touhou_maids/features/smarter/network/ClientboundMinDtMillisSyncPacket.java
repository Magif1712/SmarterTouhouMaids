package com.github.magif1712.smarter_touhou_maids.features.smarter.network;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.core.PossessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步某女仆的 Urana 快/慢环最小轮间间隔（fastMinDtMillis / slowMinDtMillis）。
 * 客户端收到后更新 {@link PossessionManager} 缓存与 maid NBT。
 * <p>
 * 参照 {@link ClientboundSmarterModeSyncPacket}，boolean → (long, long) 变体。
 */
public class ClientboundMinDtMillisSyncPacket {
    private final UUID maidUUID;
    private final long fastMinDtMillis;
    private final long slowMinDtMillis;

    public ClientboundMinDtMillisSyncPacket(UUID maidUUID, long fastMinDtMillis, long slowMinDtMillis) {
        this.maidUUID = maidUUID;
        this.fastMinDtMillis = fastMinDtMillis;
        this.slowMinDtMillis = slowMinDtMillis;
    }

    public static void encode(ClientboundMinDtMillisSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUUID);
        buf.writeLong(msg.fastMinDtMillis);
        buf.writeLong(msg.slowMinDtMillis);
    }

    public static ClientboundMinDtMillisSyncPacket decode(FriendlyByteBuf buf) {
        return new ClientboundMinDtMillisSyncPacket(buf.readUUID(), buf.readLong(), buf.readLong());
    }

    public static void handle(ClientboundMinDtMillisSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                PossessionManager.INSTANCE.onMinDtMillisSync(msg.maidUUID, msg.fastMinDtMillis, msg.slowMinDtMillis));
        });
        ctx.get().setPacketHandled(true);
    }
}
