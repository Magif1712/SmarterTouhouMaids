package com.github.magif1712.smarter_touhou_maids.features.smarter.agent;

import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ServerboundSetAiModePacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ServerboundSetSmarterModePacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.state.MaidSmarterState;
import com.github.magif1712.smarter_touhou_maids.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * smarter 通用客户端同步状态（per-maid）。
 * <p>
 * 持有"客户端已发但未收到服务端确认"的 pending 缓存，覆盖两类 smarter 通用语义：
 * <ul>
 *   <li><b>激活态 sync</b>（{@code pendingSmarterSync}）：agent isActive 边界变化时 sync 到服务端。</li>
 *   <li><b>模式选择 sync</b>（{@code pendingModeSync}）：各层 registry 的选中 entry id。</li>
 * </ul>
 * <p>
 * <b>从 PossessionManager 上提</b>（真善美第3条）：激活态 sync 和模式选择 sync 是 smarter 通用语义，
 * 原错放在 reflex_arc 的 {@code PossessionManager} 里。换 agent（非 reflex_arc）时，
 * smarter 通用层不再依赖 PossessionManager——本类承载通用缓存，PossessionManager 只留附身管理。
 * <p>
 * 设计原则（真善美第4条）：把"客户端 pending 同步缓存"这个不实在的概念，实在化为一个类。
 */
@OnlyIn(Dist.CLIENT)
public final class SmarterClientState {
    public static final SmarterClientState INSTANCE = new SmarterClientState();

    /** 激活态 pending 缓存：key = maidUUID，value = enabled。客户端已发但未收到服务端确认时读此缓存避免回显延迟。 */
    private final HashMap<UUID, Boolean> pendingSmarterSync = new HashMap<>();

    /**
     * AI 模式选择 pending 缓存：key = maidUUID，value = Map<nodeId, branchId>。
     * 一层选择一个分支，层次无限。客户端已发但未收到服务端确认时读此缓存避免读到旧值。
     */
    private final HashMap<UUID, Map<ResourceLocation, ResourceLocation>> pendingModeSync = new HashMap<>();

    /** pending 清除缓存：级联修正丢弃的插槽（已发清除包未确认）。与 pendingModeSync 互斥使用。 */
    private final HashMap<UUID, java.util.Set<ResourceLocation>> pendingModeRemovals = new HashMap<>();

    private SmarterClientState() {
    }

    // ========== 激活态 sync（per-maid）==========

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

    // ========== AI 模式选择 sync（per-maid，递归层次）==========
    // 参照 smarterMode 的 per-maid 同步模式，一层选择发一个包。
    // pendingModeSync 缓存客户端已发但未收到服务端确认的模式选择，避免回显延迟期间读到旧值。
    // 模式更改下次附身生效（与 fast/slowMinDt 一致）——运行中的 agent 不会热切换。

    public void setMode(EntityMaid maid, ResourceLocation registryId, ResourceLocation selectedId) {
        if (maid == null) return;
        pendingModeSync.computeIfAbsent(maid.getUUID(), k -> new HashMap<>()).put(registryId, selectedId);
        java.util.Set<ResourceLocation> removals = pendingModeRemovals.get(maid.getUUID());
        if (removals != null) {
            removals.remove(registryId);
        }
        NetworkHandler.INSTANCE.sendToServer(new ServerboundSetAiModePacket(maid.getUUID(), registryId, selectedId));
    }

    /**
     * 清除 maid 在指定插槽的选择（级联修正丢弃冲突条目）：pending 记录清除 + 发清除包，
     * 解析回退该插槽默认分支。
     */
    public void clearMode(EntityMaid maid, ResourceLocation registryId) {
        if (maid == null) return;
        Map<ResourceLocation, ResourceLocation> modes = pendingModeSync.get(maid.getUUID());
        if (modes != null) {
            modes.remove(registryId);
        }
        pendingModeRemovals.computeIfAbsent(maid.getUUID(), k -> new java.util.HashSet<>()).add(registryId);
        NetworkHandler.INSTANCE.sendToServer(ServerboundSetAiModePacket.clear(maid.getUUID(), registryId));
    }

    @Nullable
    public ResourceLocation getMode(EntityMaid maid, ResourceLocation registryId) {
        if (maid == null) return null;
        java.util.Set<ResourceLocation> removals = pendingModeRemovals.get(maid.getUUID());
        if (removals != null && removals.contains(registryId)) {
            return null;
        }
        Map<ResourceLocation, ResourceLocation> modes = pendingModeSync.get(maid.getUUID());
        if (modes != null && modes.containsKey(registryId)) {
            return modes.get(registryId);
        }
        return MaidSmarterState.getModeId(maid, registryId);
    }

    /**
     * 读 maid 的全部模式选择（NBT 打底，pending 覆盖——pending 优先）。
     * 供 SelectionLoader 构造 Selection（推论1：Selection 是唯一显式入参 d 的数据源收口）。
     */
    public Map<ResourceLocation, ResourceLocation> getAllModes(EntityMaid maid) {
        Map<ResourceLocation, ResourceLocation> merged = new HashMap<>();
        if (maid == null) {
            return merged;
        }
        for (String key : MaidSmarterState.getAiModes(maid).getAllKeys()) {
            ResourceLocation nodeId = ResourceLocation.tryParse(key);
            ResourceLocation branchId = ResourceLocation.tryParse(
                    MaidSmarterState.getAiModes(maid).getString(key));
            if (nodeId != null && branchId != null) {
                merged.put(nodeId, branchId);
            }
        }
        Map<ResourceLocation, ResourceLocation> pending = pendingModeSync.get(maid.getUUID());
        if (pending != null) {
            merged.putAll(pending);
        }
        java.util.Set<ResourceLocation> removals = pendingModeRemovals.get(maid.getUUID());
        if (removals != null) {
            removals.forEach(merged::remove);
        }
        return merged;
    }

    public void onAiModeSync(UUID maidUUID, ResourceLocation registryId, ResourceLocation selectedId, boolean clear) {
        if (clear) {
            Map<ResourceLocation, ResourceLocation> modes = pendingModeSync.get(maidUUID);
            if (modes != null) {
                modes.remove(registryId);
            }
            pendingModeRemovals.computeIfAbsent(maidUUID, k -> new java.util.HashSet<>()).add(registryId);
        } else {
            pendingModeSync.computeIfAbsent(maidUUID, k -> new HashMap<>()).put(registryId, selectedId);
            java.util.Set<ResourceLocation> removals = pendingModeRemovals.get(maidUUID);
            if (removals != null) {
                removals.remove(registryId);
            }
        }

        // 同步更新客户端实体 NBT，确保持久一致
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity.getUUID().equals(maidUUID) && entity instanceof EntityMaid maid) {
                    if (clear) {
                        MaidSmarterState.removeModeId(maid, registryId);
                    } else {
                        MaidSmarterState.setModeId(maid, registryId, selectedId);
                    }
                    return;
                }
            }
        }
    }

    /**
     * 清理指定 maid 的所有 pending 缓存（激活态 + 模式选择）。
     * <p>
     * 在 maid 离开客户端 level 时调用（真善美第4条：把"pending 缓存生命周期与 maid 实体绑定"
     * 这个不实在的期望，实在化为显式清理）。安全前提：第2步的 StartTracking 初始同步保证
     * maid 重新出现时 pending 会重新填充——故 chunk unload 后 reload 不丢数据。
     */
    public void removeMaid(UUID maidUUID) {
        if (maidUUID == null) return;
        pendingSmarterSync.remove(maidUUID);
        pendingModeSync.remove(maidUUID);
        pendingModeRemovals.remove(maidUUID);
    }
}