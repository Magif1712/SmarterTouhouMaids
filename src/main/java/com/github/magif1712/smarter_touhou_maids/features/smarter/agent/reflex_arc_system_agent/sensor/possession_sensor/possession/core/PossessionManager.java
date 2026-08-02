package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.core;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.config.PossessionConfig;
import com.github.magif1712.smarter_touhou_maids.network.NetworkHandler;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.network.ServerboundPossessionRequestPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.network.ServerboundSetPossessionEnabledPacket;
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