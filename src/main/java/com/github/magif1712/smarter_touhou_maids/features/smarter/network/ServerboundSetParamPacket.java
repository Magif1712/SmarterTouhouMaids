package com.github.magif1712.smarter_touhou_maids.features.smarter.network;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：设置某女仆的通用参数值（per-maid，nbtKey → textValue）。
 * <p>
 * <b>String 透传</b>（真善美第4条）：载荷为 String value，管道不感知值类型。
 * 解读（text → long/bool/…）由 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamOption}
 * 子类在客户端负责，服务端只存 String——两端都不含类型知识。
 * <p>
 * 设计原则（真善美第3条）：新增值类型时本包零改动（value 始终是 String）。
 * 第4条：把"参数同步"这个不实在的概念，实在化为一个通用包。
 * <p>
 * 服务端校验 owner 后直接写 maid NBT（modData.putString(nbtKey, value)），并回
 * {@link ClientboundParamSyncPacket} 确认。
 */
public class ServerboundSetParamPacket {
    private final UUID maidUUID;
    private final String nbtKey;
    private final String value;

    public ServerboundSetParamPacket(UUID maidUUID, String nbtKey, String value) {
        this.maidUUID = maidUUID;
        this.nbtKey = nbtKey;
        this.value = value;
    }

    public static void encode(ServerboundSetParamPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUUID);
        buf.writeUtf(msg.nbtKey);
        buf.writeUtf(msg.value);
    }

    public static ServerboundSetParamPacket decode(FriendlyByteBuf buf) {
        return new ServerboundSetParamPacket(buf.readUUID(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(ServerboundSetParamPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            Entity entity = ((ServerLevel) sender.level()).getEntity(msg.maidUUID);
            if (!(entity instanceof EntityMaid maid)) return;
            if (!sender.getUUID().equals(maid.getOwnerUUID())) return;

            // 直接写 maid NBT（通用：任何 nbtKey 都能写，已校验 owner）
            CompoundTag data = maid.getPersistentData();
            CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
            modData.putString(msg.nbtKey, msg.value);
            data.put(SmarterTouhouMaids.MOD_ID, modData);

            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> sender),
                new ClientboundParamSyncPacket(msg.maidUUID, msg.nbtKey, msg.value)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
