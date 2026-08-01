package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.config.PossessionConfig;
import com.github.magif1712.smarter_touhou_maids.network.NetworkHandler;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.network.ClientboundMaidDataSyncPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.network.ClientboundPossessionSyncPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.state.MaidSmarterState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServerPossessionManager {
    public static final ServerPossessionManager INSTANCE = new ServerPossessionManager();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY_POSSESSION_ENABLED = "PossessionEnabled";

    private final HashMap<UUID, UUID> playerToMaid = new HashMap<>();
    private final HashMap<UUID, PossessionSnapshot> originalPlayerAbilities = new HashMap<>();
    private final Map<UUID, PossessionSnapshot> offlineRecovery = new HashMap<>();

    private ServerPossessionManager() {
    }

    public void handlePossessionRequest(ServerPlayer player, UUID maidUUID, boolean start) {
        if (start) {
            startPossession(player, maidUUID);
        } else {
            if (playerToMaid.containsKey(player.getUUID())) {
                stopPossession(player.getUUID());
            }
        }
    }

    private void startPossession(ServerPlayer player, UUID maidUUID) {
        if (playerToMaid.containsKey(player.getUUID()) || playerToMaid.containsValue(maidUUID)) {
            return;
        }

        Entity entity = findEntityByUUID((ServerLevel) player.level(), maidUUID);
        if (!(entity instanceof EntityMaid maid)) {
            return;
        }

        if (!isOwner(player, maid) || !isPossessionEnabled(maid) || player.distanceToSqr(maid) > 64.0) {
            return;
        }

        originalPlayerAbilities.put(player.getUUID(), new PossessionSnapshot(player.getAbilities(), player.noPhysics, player.isInvisible()));

        playerToMaid.put(player.getUUID(), maidUUID);

        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.noPhysics = true;
        player.setInvisible(true);
        player.onUpdateAbilities();

        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ClientboundPossessionSyncPacket(true, maidUUID));
        LOGGER.info("[SmarterTouhouMaids] Player {} started possessing maid {}", player.getUUID(), maidUUID);
        player.sendSystemMessage(Component.translatable("msg.smarter_touhou_maids.possession.start"));

        // 激活状态记录：isEnabled 现语义为 agent 激活状态（由客户端 SmarterClientService 检测
        // agent isActive 边界变化时 sync，不再由 UI 开关控制）。附身启动时客户端尚未 sync，
        // 此处读到的可能是上一会话遗留状态——仅用于 log，实际激活由客户端 sync 驱动。
        if (MaidSmarterState.isEnabled(maid)) {
            LOGGER.info("[SmarterTouhouMaids] Smarter 已激活（maid {} 遗留状态）", maidUUID);
        }
    }

    public void stopPossession(UUID playerUUID) {
        if (!playerToMaid.containsKey(playerUUID)) {
            return;
        }

        UUID maidUUID = playerToMaid.remove(playerUUID);
        PossessionSnapshot snapshot = originalPlayerAbilities.remove(playerUUID);

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);

        if (player != null && snapshot != null) {
            snapshot.restore(player);
            player.onUpdateAbilities();
            player.sendSystemMessage(Component.translatable("msg.smarter_touhou_maids.possession.stop"));
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ClientboundPossessionSyncPacket(false, Util.NIL_UUID));
        } else if (snapshot != null) {
            offlineRecovery.put(playerUUID, snapshot);
        }

        LOGGER.info("[SmarterTouhouMaids] Player {} stopped possessing maid {}", playerUUID, maidUUID);

        // smarter 失活由客户端驱动：取消附身后 SmarterClientService 检测 isActive=false
        // （ReflexArcSystemAgent.isActive=附身）→ sync MaidSmarterState.setEnabled(false)，
        // 本 mixin 守卫放行 serverAiStep，原版 AI 复原。服务端无需主动停止。
    }

    public void setPossessionEnabled(ServerPlayer player, UUID maidUUID, boolean enabled) {
        Entity entity = findEntityByUUID((ServerLevel) player.level(), maidUUID);
        if (!(entity instanceof EntityMaid maid)) {
            return;
        }

        if (!isOwner(player, maid)) {
            return;
        }

        CompoundTag data = maid.getPersistentData();
        CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
        modData.putBoolean(KEY_POSSESSION_ENABLED, enabled);
        data.put(SmarterTouhouMaids.MOD_ID, modData);

        if (!enabled) {
            playerToMaid.entrySet().stream()
                    .filter(entry -> maidUUID.equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .ifPresent(this::stopPossession);
        }

        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ClientboundMaidDataSyncPacket(maidUUID, enabled));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(playerToMaid.entrySet())) {
            UUID playerUUID = entry.getKey();
            UUID maidUUID = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            if (player == null) {
                stopPossession(playerUUID);
                continue;
            }

            Entity entity = findEntityByUUID((ServerLevel) player.level(), maidUUID);
            if (!(entity instanceof EntityMaid maid) || !maid.isAlive()) {
                stopPossession(playerUUID);
                continue;
            }

            if (player.level() != maid.level()) {
                stopPossession(playerUUID);
                continue;
            }

            player.moveTo(maid.getX(), maid.getY(), maid.getZ(), maid.getYRot(), maid.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = false;
            player.fallDistance = 0;
        }
    }

    @SubscribeEvent
    public void onPlayerLogOut(PlayerEvent.PlayerLoggedOutEvent event) {
        stopPossession(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        PossessionSnapshot snapshot = offlineRecovery.remove(event.getEntity().getUUID());
        if (snapshot != null && event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            snapshot.restore(player);
            player.onUpdateAbilities();
            LOGGER.info("[SmarterTouhouMaids] Restored abilities for player {} after re-login.", player.getUUID());
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityMaid) {
            UUID maidUUID = event.getEntity().getUUID();
            if (playerToMaid.containsValue(maidUUID)) {
                UUID playerUUID = playerToMaid.entrySet().stream()
                        .filter(entry -> maidUUID.equals(entry.getValue()))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);
                if (playerUUID != null) {
                    stopPossession(playerUUID);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        stopPossession(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        stopPossession(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (playerToMaid.containsKey(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (playerToMaid.containsKey(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (playerToMaid.containsKey(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (playerToMaid.containsKey(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
            && playerToMaid.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
            && playerToMaid.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    private boolean isOwner(Player player, EntityMaid maid) {
        return player.getUUID().equals(maid.getOwnerUUID());
    }

    public static boolean isPossessionEnabled(EntityMaid maid) {
        try {
            CompoundTag data = maid.getPersistentData();
            if (data.contains(SmarterTouhouMaids.MOD_ID, 10)) {
                CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
                if (modData.contains(KEY_POSSESSION_ENABLED, 1)) {
                    return modData.getBoolean(KEY_POSSESSION_ENABLED);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read possession NBT from maid {}", maid.getUUID(), e);
        }
        return PossessionConfig.enabled.get();
    }

    @Nullable
    private Entity findEntityByUUID(ServerLevel level, UUID uuid) {
        return level.getEntity(uuid);
    }

    private static class PossessionSnapshot {
        boolean mayfly, flying, invisible;
        float flyingSpeed, walkingSpeed;
        boolean noPhysics;

        PossessionSnapshot(Abilities abilities, boolean noPhysics, boolean invisible) {
            this.mayfly = abilities.mayfly;
            this.flying = abilities.flying;
            this.flyingSpeed = abilities.getFlyingSpeed();
            this.walkingSpeed = abilities.getWalkingSpeed();
            this.noPhysics = noPhysics;
            this.invisible = invisible;
        }

        void restore(Player player) {
            player.getAbilities().mayfly = this.mayfly;
            player.getAbilities().flying = this.flying;
            player.getAbilities().setFlyingSpeed(this.flyingSpeed);
            player.getAbilities().setWalkingSpeed(this.walkingSpeed);
            player.noPhysics = this.noPhysics;
            player.setInvisible(this.invisible);
        }
    }
}