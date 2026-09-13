package com.github.magif1712.smarter_touhou_maids.features.smarter.network;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步某女仆在指定插槽的模式选择。
 * 客户端收到后更新 {@link SmarterClientState} 缓存与 maid NBT。
 * <p>
 * clear=true 表示该插槽的已存选择被清除（级联修正丢弃冲突条目），解析回退默认分支。
 */
public class ClientboundAiModeSyncPacket {
    private final UUID maidUUID;
    private final ResourceLocation registryId;
    private final ResourceLocation selectedId;
    private final boolean clear;

    public ClientboundAiModeSyncPacket(UUID maidUUID, ResourceLocation registryId, ResourceLocation selectedId) {
        this(maidUUID, registryId, selectedId, false);
    }

    public ClientboundAiModeSyncPacket(UUID maidUUID, ResourceLocation registryId, ResourceLocation selectedId, boolean clear) {
        this.maidUUID = maidUUID;
        this.registryId = registryId;
        this.selectedId = selectedId;
        this.clear = clear;
    }

    public static void encode(ClientboundAiModeSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUUID);
        buf.writeResourceLocation(msg.registryId);
        buf.writeResourceLocation(msg.selectedId);
        buf.writeBoolean(msg.clear);
    }

    public static ClientboundAiModeSyncPacket decode(FriendlyByteBuf buf) {
        return new ClientboundAiModeSyncPacket(buf.readUUID(), buf.readResourceLocation(), buf.readResourceLocation(), buf.readBoolean());
    }

    public static void handle(ClientboundAiModeSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                SmarterClientState.INSTANCE.onAiModeSync(msg.maidUUID, msg.registryId, msg.selectedId, msg.clear));
        });
        ctx.get().setPacketHandled(true);
    }
}
