package com.github.magif1712.smarter_touhou_maids.features.smarter.network;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
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
 * 客户端 → 服务端：设置某女仆在指定插槽的模式选择。
 * <p>
 * 一层选择发一个包（registryId 标识是哪个插槽：agent / ai / process / nn / 附属的新插槽），
 * 层次无限也能处理，非三 id 一包。
 * <p>
 * <b>clear 语义</b>（级联修正丢弃冲突条目）：clear=true 时清除该插槽的已存选择
 * （解析回退默认分支），selectedId 字段填 {@link #CLEAR_DUMMY} 占位（ResourceLocation 不可空）。
 * <p>
 * 服务端校验 owner 后写入 maid NBT（{@link MaidSmarterState#setModeId} / {@code removeModeId}），
 * 并回 {@link ClientboundAiModeSyncPacket} 确认。模式更改下次附身生效（与 fast/slowMinDt 一致）。
 */
public class ServerboundSetAiModePacket {
    /** clear=true 时的占位 selectedId（协议字段不可空，无语义）。 */
    public static final ResourceLocation CLEAR_DUMMY = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "none");

    private final UUID maidUUID;
    private final ResourceLocation registryId;
    private final ResourceLocation selectedId;
    private final boolean clear;

    public ServerboundSetAiModePacket(UUID maidUUID, ResourceLocation registryId, ResourceLocation selectedId) {
        this(maidUUID, registryId, selectedId, false);
    }

    private ServerboundSetAiModePacket(UUID maidUUID, ResourceLocation registryId, ResourceLocation selectedId, boolean clear) {
        this.maidUUID = maidUUID;
        this.registryId = registryId;
        this.selectedId = selectedId;
        this.clear = clear;
    }

    /** 构造清除包（级联修正丢弃该插槽的已存选择）。 */
    public static ServerboundSetAiModePacket clear(UUID maidUUID, ResourceLocation registryId) {
        return new ServerboundSetAiModePacket(maidUUID, registryId, CLEAR_DUMMY, true);
    }

    public static void encode(ServerboundSetAiModePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUUID);
        buf.writeResourceLocation(msg.registryId);
        buf.writeResourceLocation(msg.selectedId);
        buf.writeBoolean(msg.clear);
    }

    public static ServerboundSetAiModePacket decode(FriendlyByteBuf buf) {
        return new ServerboundSetAiModePacket(buf.readUUID(), buf.readResourceLocation(), buf.readResourceLocation(), buf.readBoolean());
    }

    public static void handle(ServerboundSetAiModePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            Entity entity = ((ServerLevel) sender.level()).getEntity(msg.maidUUID);
            if (!(entity instanceof EntityMaid maid)) return;
            if (!sender.getUUID().equals(maid.getOwnerUUID())) return;

            if (msg.clear) {
                MaidSmarterState.removeModeId(maid, msg.registryId);
            } else {
                MaidSmarterState.setModeId(maid, msg.registryId, msg.selectedId);
            }
            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> sender),
                new ClientboundAiModeSyncPacket(msg.maidUUID, msg.registryId, msg.selectedId, msg.clear)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
