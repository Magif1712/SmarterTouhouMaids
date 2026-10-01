package com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.network;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.state.MaidSmarterState;
import com.github.magif1712.smarter_touhou_maids.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

/**
 * 服务端初始同步：玩家开始追踪 EntityMaid 时，将该 maid 的所有 AiModes + ParamStore 参数推送给客户端。
 * <p>
 * <b>根因</b>：Forge 的 {@code Entity.getPersistentData()} 不自动双端同步（服务端专属），
 * 客户端 maid 的 persistentData 天然为空。客户端重启重连后 pendingModeSync/pendingCache 清空，
 * init() 的 {@code SmarterClientState.getMode()} 两级都 miss → fallback 默认模式。
 * <p>
 * <b>修复</b>（真善美第4条）：把"服务端的模式选择对客户端可见"这个不实在的假设，
 * 实在化为 {@code PlayerEvent.StartTracking} 时的主动推送。客户端收到后走已有的
 * {@code SmarterClientState.onAiModeSync()} / {@code ParamStore.onSync()} 写入
 * pending 缓存 + 客户端 NBT → 后续 init() 的 getMode() 可读到正确值。
 * <p>
 * <b>复用现有包</b>（真善美第3条）：逐层发 {@link ClientboundAiModeSyncPacket} /
 * {@link ClientboundParamSyncPacket}，不引入新包类型。换层/换参数时本类零改动
 * （遍历 NBT key，不硬编码任何 registryId / nbtKey）。
 */
public class SmarterTrackingSync {

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof EntityMaid maid)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getUUID().equals(maid.getOwnerUUID())) return;

        UUID maidUUID = maid.getUUID();
        var channel = PacketDistributor.PLAYER.with(() -> player);

        syncAiModes(maid, maidUUID, channel);
        syncParams(maid, maidUUID, channel);
        syncEnabled(maid, maidUUID, channel);
    }

    /**
     * 同步 AiModes：遍历 maid NBT 中的 AiModes CompoundTag，逐层发 {@link ClientboundAiModeSyncPacket}。
     * <p>
     * 不硬编码 registryId（真善美第3条）：遍历 NBT key，附属模组新增的层自动同步。
     * AiModes 不存在时跳过（旧存档，客户端 fallback 默认）。
     */
    private void syncAiModes(EntityMaid maid, UUID maidUUID, PacketDistributor.PacketTarget channel) {
        CompoundTag aiModes = MaidSmarterState.getAiModes(maid);
        for (String key : aiModes.getAllKeys()) {
            String value = aiModes.getString(key);
            ResourceLocation registryId = ResourceLocation.tryParse(key);
            ResourceLocation selectedId = ResourceLocation.tryParse(value);
            if (registryId != null && selectedId != null) {
                NetworkHandler.INSTANCE.send(channel,
                        new ClientboundAiModeSyncPacket(maidUUID, registryId, selectedId));
            }
        }
    }

    /**
     * 同步 ParamStore 参数：遍历 maid persistentData 中 modData 下除 AiModes 和
     * SmarterOnPossession 外的所有 key，逐个发 {@link ClientboundParamSyncPacket}。
     * <p>
     * 不硬编码 nbtKey（真善美第3条）：遍历 NBT key，附属模组/新参数自动同步。
     * 只发 String 值（TAG_STRING）和 long 值（TAG_LONG，兼容旧存档）。
     */
    private void syncParams(EntityMaid maid, UUID maidUUID, PacketDistributor.PacketTarget channel) {
        CompoundTag data = maid.getPersistentData();
        if (!data.contains(SmarterTouhouMaids.MOD_ID, 10)) return;
        CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);

        for (String key : modData.getAllKeys()) {
            if (key.equals("AiModes") || key.equals("SmarterOnPossession")) continue;
            if (modData.contains(key, 8)) {
                NetworkHandler.INSTANCE.send(channel,
                        new ClientboundParamSyncPacket(maidUUID, key, modData.getString(key)));
            } else if (modData.contains(key, 4)) {
                NetworkHandler.INSTANCE.send(channel,
                        new ClientboundParamSyncPacket(maidUUID, key, String.valueOf(modData.getLong(key))));
            }
        }
    }

    /**
     * 同步激活状态：发 {@link ClientboundSmarterModeSyncPacket}。
     */
    private void syncEnabled(EntityMaid maid, UUID maidUUID, PacketDistributor.PacketTarget channel) {
        boolean enabled = MaidSmarterState.isEnabled(maid);
        NetworkHandler.INSTANCE.send(channel,
                new ClientboundSmarterModeSyncPacket(maidUUID, enabled));
    }
}