package com.github.magif1712.smarter_touhou_maids.features.smarter.network;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步确认某女仆的通用参数值（per-maid，nbtKey → textValue）。
 * <p>
 * <b>String 透传</b>（真善美第4条）：载荷为 String value，管道不感知值类型。
 * 客户端收到后调 {@link ParamStore#onSync} 更新 pending 缓存与 maid NBT。
 * <p>
 * 设计原则（真善美第3条）：新增值类型时本包零改动（value 始终是 String）。
 */
public class ClientboundParamSyncPacket {
    private final UUID maidUUID;
    private final String nbtKey;
    private final String value;

    public ClientboundParamSyncPacket(UUID maidUUID, String nbtKey, String value) {
        this.maidUUID = maidUUID;
        this.nbtKey = nbtKey;
        this.value = value;
    }

    public static void encode(ClientboundParamSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUUID);
        buf.writeUtf(msg.nbtKey);
        buf.writeUtf(msg.value);
    }

    public static ClientboundParamSyncPacket decode(FriendlyByteBuf buf) {
        return new ClientboundParamSyncPacket(buf.readUUID(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(ClientboundParamSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ParamStore.INSTANCE.onSync(msg.maidUUID, msg.nbtKey, msg.value));
        });
        ctx.get().setPacketHandled(true);
    }
}
