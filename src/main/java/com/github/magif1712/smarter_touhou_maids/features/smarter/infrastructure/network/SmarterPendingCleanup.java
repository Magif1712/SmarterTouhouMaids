package com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.network;

import com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.runtime.SmarterClientState;
import com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.param.ParamStore;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 客户端 pending 缓存清理：EntityMaid 离开客户端 level 时清理其 pendingModeSync / pendingCache。
 * <p>
 * <b>根因</b>：pendingModeSync 和 pendingCache 从不清理，maid 离开世界后对应 UUID 的条目
 * 永远残留 → 内存泄漏（每个曾操作过的 maid 的 UUID 都留在 HashMap 里）。
 * <p>
 * <b>修复</b>（真善美第4条）：把"pending 缓存生命周期与 maid 实体绑定"这个不实在的期望，
 * 实在化为 {@code EntityLeaveLevelEvent} 时的显式清理。
 * <p>
 * <b>安全前提</b>：第2步的 {@link SmarterTrackingSync}（StartTracking 初始同步）保证
 * maid 重新出现时 pending 会重新填充——故 chunk unload 后 reload 不丢数据。
 * 清理只在 maid 离开客户端 level 时触发，不影响同一会话内的正常使用。
 */
@OnlyIn(Dist.CLIENT)
public class SmarterPendingCleanup {

    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (!event.getLevel().isClientSide()) return;

        java.util.UUID maidUUID = maid.getUUID();
        SmarterClientState.INSTANCE.removeMaid(maidUUID);
        ParamStore.INSTANCE.removeMaid(maidUUID);
    }
}