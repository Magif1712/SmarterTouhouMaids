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
 * 服务端 → 客户端：同步某女仆在指定 registry 层的 AI 模式选择。
 * 客户端收到后更新 {@link SmarterClientState} 缓存与 maid NBT。
 * <p>
 * 参照 {@link ClientboundParamSyncPacket}，(String, long) → (ResourceLocation, ResourceLocation) 变体。
 */
public class ClientboundAiModeSyncPacket {
    private final UUID maidUUID;
    private final ResourceLocation registryId;
    private final ResourceLocation selectedId;

    public ClientboundAiModeSyncPacket(UUID maidUUID, ResourceLocation registryId, ResourceLocation selectedId) {
        this.maidUUID = maidUUID;
        this.registryId = registryId;
        this.selectedId = selectedId;
    }

    public static void encode(ClientboundAiModeSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUUID);
        buf.writeResourceLocation(msg.registryId);
        buf.writeResourceLocation(msg.selectedId);
    }

    public static ClientboundAiModeSyncPacket decode(FriendlyByteBuf buf) {
        return new ClientboundAiModeSyncPacket(buf.readUUID(), buf.readResourceLocation(), buf.readResourceLocation());
    }

    public static void handle(ClientboundAiModeSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                SmarterClientState.INSTANCE.onAiModeSync(msg.maidUUID, msg.registryId, msg.selectedId));
        });
        ctx.get().setPacketHandled(true);
    }
}
