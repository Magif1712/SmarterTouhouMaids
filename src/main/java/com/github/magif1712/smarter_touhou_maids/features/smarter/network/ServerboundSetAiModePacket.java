package com.github.magif1712.smarter_touhou_maids.features.smarter.network;

import com.github.magif1712.smarter_touhou_maids.features.smarter.state.MaidSmarterState;
import com.github.magif1712.smarter_touhou_maids.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：设置某女仆在指定 registry 层的 AI 模式选择。
 * <p>
 * 一层选择发一个包（registryId 标识是哪一层：agent / ai / process / nn / 附属的新层），
 * 层次无限也能处理，非三 id 一包。
 * <p>
 * 服务端校验 owner 后写入 maid NBT（{@link MaidSmarterState#setModeId}），
 * 并回 {@link ClientboundAiModeSyncPacket} 确认。模式更改下次附身生效（与 fast/slowMinDt 一致）。
 * <p>
 * 参照 {@link ServerboundSetMinDtMillisPacket}，(long, long) → (ResourceLocation, ResourceLocation) 变体。
 */
public class ServerboundSetAiModePacket {
    private final UUID maidUUID;
    private final ResourceLocation registryId;
    private final ResourceLocation selectedId;

    public ServerboundSetAiModePacket(UUID maidUUID, ResourceLocation registryId, ResourceLocation selectedId) {
        this.maidUUID = maidUUID;
        this.registryId = registryId;
        this.selectedId = selectedId;
    }

    public static void encode(ServerboundSetAiModePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUUID);
        buf.writeResourceLocation(msg.registryId);
        buf.writeResourceLocation(msg.selectedId);
    }

    public static ServerboundSetAiModePacket decode(FriendlyByteBuf buf) {
        return new ServerboundSetAiModePacket(buf.readUUID(), buf.readResourceLocation(), buf.readResourceLocation());
    }

    public static void handle(ServerboundSetAiModePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            Entity entity = ((ServerLevel) sender.level()).getEntity(msg.maidUUID);
            if (!(entity instanceof EntityMaid maid)) return;
            if (!sender.getUUID().equals(maid.getOwnerUUID())) return;

            MaidSmarterState.setModeId(maid, msg.registryId, msg.selectedId);
            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> sender),
                new ClientboundAiModeSyncPacket(msg.maidUUID, msg.registryId, msg.selectedId)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
