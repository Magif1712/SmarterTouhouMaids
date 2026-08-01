package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.core;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.config.PossessionConfig;
import com.github.magif1712.smarter_touhou_maids.network.NetworkHandler;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.network.ServerboundPossessionRequestPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.network.ServerboundSetPossessionEnabledPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ServerboundSetAiModePacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ServerboundSetMinDtMillisPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ServerboundSetSmarterModePacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.state.MaidSmarterState;
import com.github.magif1712.smarter_touhou_maids.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class PossessionManager {
    public static final PossessionManager INSTANCE = new PossessionManager();
    private static final Logger LOGGER = LogUtils.getLogger();

    private boolean isPossessing = false;
    @Nullable
    private UUID possessedMaidUUID = null;
    @Nullable
    private EntityMaid possessedMaidCache = null;

    private final ArrayDeque<UUID> pendingPossessionRequest = new ArrayDeque<>();
    private final HashMap<UUID, Boolean> pendingDataSync = new HashMap<>();
    private final HashMap<UUID, Boolean> pendingSmarterSync = new HashMap<>();
    private final HashMap<UUID, Long> pendingFastMinDtSync = new HashMap<>();
    private final HashMap<UUID, Long> pendingSlowMinDtSync = new HashMap<>();
    /**
     * AI 模式选择 pending 缓存：key = maidUUID，value = Map<registryId, selectedId>。
     * 一层选择一个 entry，层次无限。客户端已发但未收到服务端确认时读此缓存避免回显延迟期间读到旧值。
     */
    private final HashMap<UUID, Map<ResourceLocation, ResourceLocation>> pendingModeSync = new HashMap<>();
    private boolean ghostRecoveryPending = false;

    @Nullable
    private LocalPlayer lastPlayer = null;

    private PossessionManager() {
    }

    // Public API
    public void requestPossession(EntityMaid maid) {
        if (maid == null) return;
        NetworkHandler.INSTANCE.sendToServer(new ServerboundPossessionRequestPacket(maid.getUUID(), true));
    }

    public void requestStopPossession() {
        if (isPossessing()) {
            NetworkHandler.INSTANCE.sendToServer(new ServerboundPossessionRequestPacket(Util.NIL_UUID, false));
        }
    }

    public void setPossessionEnabled(EntityMaid maid, boolean enabled) {
        if (maid == null) return;
        pendingDataSync.put(maid.getUUID(), enabled);
        NetworkHandler.INSTANCE.sendToServer(new ServerboundSetPossessionEnabledPacket(maid.getUUID(), enabled));
    }

    public boolean isPossessionEnabled(EntityMaid maid) {
        if (pendingDataSync.containsKey(maid.getUUID())) {
            return pendingDataSync.get(maid.getUUID());
        }
        try {
            CompoundTag data = maid.getPersistentData();
            if (data.contains(SmarterTouhouMaids.MOD_ID, 10)) {
                CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
                if (modData.contains("PossessionEnabled", 1)) {
                    return modData.getBoolean("PossessionEnabled");
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read possession NBT from maid {}", maid.getUUID(), e);
        }
        return PossessionConfig.enabled.get();
    }

    public void setSmarterModeEnabled(EntityMaid maid, boolean enabled) {
        if (maid == null) return;
        setSmarterModeEnabled(maid.getUUID(), enabled);
    }

    /**
     * 按 UUID sync 激活状态（供 shutdown 时 maid 已 null 的场景，如取消附身后 getPossessedMaid 立即失效）。
     * <p>
     * 激活状态由 agent isActive 边界变化驱动（替代旧 smarter UI 开关 sync）：
     * SmarterClientService 检测 isActive true→false / false→true 时调用。
     */
    public void setSmarterModeEnabled(UUID maidUUID, boolean enabled) {
        if (maidUUID == null) return;
        pendingSmarterSync.put(maidUUID, enabled);
        NetworkHandler.INSTANCE.sendToServer(new ServerboundSetSmarterModePacket(maidUUID, enabled));
    }

    public void onSmarterModeSync(UUID maidUUID, boolean enabled) {
        pendingSmarterSync.put(maidUUID, enabled);

        // 同步更新客户端实体 NBT，确保持久一致
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity.getUUID().equals(maidUUID) && entity instanceof EntityMaid maid) {
                    MaidSmarterState.setEnabled(maid, enabled);
                    return;
                }
            }
        }
    }

    // ========== Urana 快/慢环最小轮间间隔（per-maid）==========
    // 参照 smarterMode 的 per-maid 同步模式，boolean → (long, long) 变体。
    // 两环作为同一女仆的 urana 节律配置对一起同步：set 一环时捎带另一环当前值，避免新增包类。

    public void setFastMinDtMillis(EntityMaid maid, long fastMinDtMillis) {
        if (maid == null) return;
        pendingFastMinDtSync.put(maid.getUUID(), fastMinDtMillis);
        // 捎带慢环当前值一起发包（值不变，仅满足 payload 一变二）
        long slowMinDtMillis = getSlowMinDtMillis(maid);
        NetworkHandler.INSTANCE.sendToServer(new ServerboundSetMinDtMillisPacket(maid.getUUID(), fastMinDtMillis, slowMinDtMillis));
    }

    public void setSlowMinDtMillis(EntityMaid maid, long slowMinDtMillis) {
        if (maid == null) return;
        pendingSlowMinDtSync.put(maid.getUUID(), slowMinDtMillis);
        // 捎带快环当前值一起发包（值不变，仅满足 payload 一变二）
        long fastMinDtMillis = getFastMinDtMillis(maid);
        NetworkHandler.INSTANCE.sendToServer(new ServerboundSetMinDtMillisPacket(maid.getUUID(), fastMinDtMillis, slowMinDtMillis));
    }

    public long getFastMinDtMillis(EntityMaid maid) {
        if (maid == null) return 0;
        if (pendingFastMinDtSync.containsKey(maid.getUUID())) {
            return pendingFastMinDtSync.get(maid.getUUID());
        }
        return MaidSmarterState.getFastMinDtMillis(maid);
    }

    public long getSlowMinDtMillis(EntityMaid maid) {
        if (maid == null) return 0;
        if (pendingSlowMinDtSync.containsKey(maid.getUUID())) {
            return pendingSlowMinDtSync.get(maid.getUUID());
        }
        return MaidSmarterState.getSlowMinDtMillis(maid);
    }

    public void onMinDtMillisSync(UUID maidUUID, long fastMinDtMillis, long slowMinDtMillis) {
        pendingFastMinDtSync.put(maidUUID, fastMinDtMillis);
        pendingSlowMinDtSync.put(maidUUID, slowMinDtMillis);

        // 同步更新客户端实体 NBT，确保持久一致
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity.getUUID().equals(maidUUID) && entity instanceof EntityMaid maid) {
                    MaidSmarterState.setFastMinDtMillis(maid, fastMinDtMillis);
                    MaidSmarterState.setSlowMinDtMillis(maid, slowMinDtMillis);
                    return;
                }
            }
        }
    }

    // ========== AI 模式选择（per-maid，递归层次）==========
    // 参照 smarterMode/minDtMillis 的 per-maid 同步模式，一层选择发一个包。
    // pendingModeSync 缓存客户端已发但未收到服务端确认的模式选择，避免回显延迟期间读到旧值。
    // 模式更改下次附身生效（与 fast/slowMinDt 一致）——运行中的 agent 不会热切换。

    public void setMode(EntityMaid maid, ResourceLocation registryId, ResourceLocation selectedId) {
        if (maid == null) return;
        pendingModeSync.computeIfAbsent(maid.getUUID(), k -> new HashMap<>()).put(registryId, selectedId);
        NetworkHandler.INSTANCE.sendToServer(new ServerboundSetAiModePacket(maid.getUUID(), registryId, selectedId));
    }

    @Nullable
    public ResourceLocation getMode(EntityMaid maid, ResourceLocation registryId) {
        if (maid == null) return null;
        Map<ResourceLocation, ResourceLocation> modes = pendingModeSync.get(maid.getUUID());
        if (modes != null && modes.containsKey(registryId)) {
            return modes.get(registryId);
        }
        return MaidSmarterState.getModeId(maid, registryId);
    }

    public void onAiModeSync(UUID maidUUID, ResourceLocation registryId, ResourceLocation selectedId) {
        pendingModeSync.computeIfAbsent(maidUUID, k -> new HashMap<>()).put(registryId, selectedId);

        // 同步更新客户端实体 NBT，确保持久一致
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity.getUUID().equals(maidUUID) && entity instanceof EntityMaid maid) {
                    MaidSmarterState.setModeId(maid, registryId, selectedId);
                    return;
                }
            }
        }
    }

    // State Checkers
    public boolean isPossessing() {
        return isPossessing && possessedMaidUUID != null;
    }

    @Nullable
    public EntityMaid getPossessedMaid() {
        if (isPossessing() && possessedMaidCache != null && possessedMaidCache.isAlive()) {
            return possessedMaidCache;
        }
        return null;
    }

    // Event Handlers & State Logic
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer currentPlayer = mc.player;

        if (lastPlayer != currentPlayer) {
            if (isPossessing) {
                forceReset();
            }
            lastPlayer = currentPlayer;
            if (currentPlayer == null) return;
        }

        if (currentPlayer == null) return;

        tryStartPossession();
        trySyncPendingData();

        if (isPossessing()) {
            if (possessedMaidCache == null || !possessedMaidCache.isAlive()) {
                if (pendingPossessionRequest.isEmpty() && !ghostRecoveryPending) {
                    ghostRecoveryPending = true;
                    // Local cleanup, no network request
                    this.isPossessing = false;
                    this.possessedMaidUUID = null;
                    this.possessedMaidCache = null;
                    if (mc.player != null && mc.getCameraEntity() instanceof EntityMaid) {
                        mc.setCameraEntity(mc.player);
                    }
                }
            } else {
                ghostRecoveryPending = false; // Status is normal
                if (mc.player != null && possessedMaidCache != null) {
                    mc.player.moveTo(
                        possessedMaidCache.getX(),
                        possessedMaidCache.getY(),
                        possessedMaidCache.getZ(),
                        possessedMaidCache.getYRot(),
                        possessedMaidCache.getXRot()
                    );
                }
            }
        }
    }

    public void forceReset() {
        this.isPossessing = false;
        this.possessedMaidUUID = null;
        this.possessedMaidCache = null;
        this.pendingPossessionRequest.clear();
        this.pendingDataSync.clear();
        this.ghostRecoveryPending = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getCameraEntity() != null && mc.getCameraEntity() != mc.player) {
            mc.setCameraEntity(mc.player);
        }
    }

    private void tryStartPossession() {
        UUID pending = pendingPossessionRequest.peek();
        if (pending == null) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.level() == null) return;

        List<EntityMaid> maids = player.level().getEntitiesOfClass(EntityMaid.class, player.getBoundingBox().inflate(256.0), e -> e.getUUID().equals(pending));
        if (!maids.isEmpty()) {
            this.possessedMaidUUID = pendingPossessionRequest.poll();
            this.possessedMaidCache = maids.get(0);
            Minecraft.getInstance().setCameraEntity(this.possessedMaidCache);
        }
    }

    // TODO: If performance becomes an issue with many pending syncs, consider a local maid cache map or reducing the search radius.
    private void trySyncPendingData() {
        if (pendingDataSync.isEmpty()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.level() == null) return;

        Iterator<Map.Entry<UUID, Boolean>> it = pendingDataSync.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Boolean> entry = it.next();
            UUID maidUUID = entry.getKey();

            List<EntityMaid> maids = player.level().getEntitiesOfClass(EntityMaid.class, player.getBoundingBox().inflate(256.0), e -> e.getUUID().equals(maidUUID));
            if (!maids.isEmpty()) {
                EntityMaid maid = maids.get(0);
                boolean enabled = entry.getValue();
                CompoundTag data = maid.getPersistentData();
                CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
                modData.putBoolean("PossessionEnabled", enabled);
                data.put(SmarterTouhouMaids.MOD_ID, modData);
                it.remove();
            }
        }
    }

    // Network Sync Handlers
    public void onServerSync(boolean possessing, @Nullable UUID maidUUID) {
        Minecraft mc = Minecraft.getInstance();
        if (possessing) {
            if (this.isPossessing && this.possessedMaidUUID != null && !this.possessedMaidUUID.equals(maidUUID)) {
                LOGGER.warn("Received a possession start request for {} while already possessing {}. Ignoring.", maidUUID, this.possessedMaidUUID);
                return;
            }
            this.isPossessing = true;
            if (maidUUID != null) {
                this.pendingPossessionRequest.add(maidUUID);
            }
        } else {
            this.isPossessing = false;
            this.possessedMaidUUID = null;
            this.possessedMaidCache = null;
            this.pendingPossessionRequest.clear();

            if (mc.player != null && mc.getCameraEntity() != mc.player) {
                mc.setCameraEntity(mc.player);
            }
            this.ghostRecoveryPending = false;
        }
    }

    public void onMaidDataSync(UUID maidUUID, boolean enabled) {
        pendingDataSync.put(maidUUID, enabled);
    }
}