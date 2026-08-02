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
     * AI 模式选择 pending 缓存：key = maidUUID，value = Map<registryId, selectedId>。
     * 一层选择一个 entry，层次无限。客户端已发但未收到服务端确认时读此缓存避免读到旧值。
     */
    private final HashMap<UUID, Map<ResourceLocation, ResourceLocation>> pendingModeSync = new HashMap<>();

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
}
