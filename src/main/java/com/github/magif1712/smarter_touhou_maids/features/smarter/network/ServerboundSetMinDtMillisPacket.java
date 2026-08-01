package com.github.magif1712.smarter_touhou_maids.features.smarter.network;

import com.github.magif1712.smarter_touhou_maids.features.smarter.state.MaidSmarterState;
import com.github.magif1712.smarter_touhou_maids.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：设置某女仆的 Urana 快/慢环最小轮间间隔（fastMinDtMillis / slowMinDtMillis）。
 * 服务端校验 owner 后写入 maid NBT，并回 {@link ClientboundMinDtMillisSyncPacket} 确认。
 * <p>
 * 两环作为同一女仆的 urana 节律配置对一起同步：GUI 改一环时，另一环的当前值一并捎带（不变），
 * 避免新增包类与注册（payload 一变二，包类零新增）。
 * <p>
 * 参照 {@link ServerboundSetSmarterModePacket}，boolean → (long, long) 变体。
 */
public class ServerboundSetMinDtMillisPacket {
    private final UUID maidUUID;
    private final long fastMinDtMillis;
    private final long slowMinDtMillis;

    public ServerboundSetMinDtMillisPacket(UUID maidUUID, long fastMinDtMillis, long slowMinDtMillis) {
        this.maidUUID = maidUUID;
        this.fastMinDtMillis = fastMinDtMillis;
        this.slowMinDtMillis = slowMinDtMillis;
    }

    public static void encode(ServerboundSetMinDtMillisPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUUID);
        buf.writeLong(msg.fastMinDtMillis);
        buf.writeLong(msg.slowMinDtMillis);
    }

    public static ServerboundSetMinDtMillisPacket decode(FriendlyByteBuf buf) {
        return new ServerboundSetMinDtMillisPacket(buf.readUUID(), buf.readLong(), buf.readLong());
    }

    public static void handle(ServerboundSetMinDtMillisPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            Entity entity = ((ServerLevel) sender.level()).getEntity(msg.maidUUID);
            if (!(entity instanceof EntityMaid maid)) return;
            if (!sender.getUUID().equals(maid.getOwnerUUID())) return;

            MaidSmarterState.setFastMinDtMillis(maid, msg.fastMinDtMillis);
            MaidSmarterState.setSlowMinDtMillis(maid, msg.slowMinDtMillis);
            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> sender),
                new ClientboundMinDtMillisSyncPacket(msg.maidUUID, msg.fastMinDtMillis, msg.slowMinDtMillis)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
