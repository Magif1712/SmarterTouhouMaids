package com.github.magif1712.smarter_touhou_maids.features.smarter.network;

import com.github.magif1712.smarter_touhou_maids.features.smarter.state.MaidSmarterState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundBehaviorSyncPacket {
    private final int maidId;
    private final long[] behavior;

    public ServerboundBehaviorSyncPacket(int maidId, long[] behavior) {
        this.maidId = maidId;
        this.behavior = behavior;
    }

    public static void encode(ServerboundBehaviorSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.maidId);
        buf.writeVarInt(msg.behavior.length);
        for (long v : msg.behavior) {
            buf.writeLong(v);
        }
    }

    public static ServerboundBehaviorSyncPacket decode(FriendlyByteBuf buf) {
        int maidId = buf.readVarInt();
        int len = buf.readVarInt();
        long[] behavior = new long[len];
        for (int i = 0; i < len; i++) {
            behavior[i] = buf.readLong();
        }
        return new ServerboundBehaviorSyncPacket(maidId, behavior);
    }

    public static void handle(ServerboundBehaviorSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            Entity entity = sender.level().getEntity(msg.maidId);
            if (!(entity instanceof EntityMaid maid)) return;
            if (!sender.getUUID().equals(maid.getOwnerUUID())) return;
            if (!MaidSmarterState.isEnabled(maid)) return;

            MaidSmarterState.setBehavior(maid, msg.behavior);
        });
        ctx.get().setPacketHandled(true);
    }
}