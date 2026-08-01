package com.github.magif1712.smarter_touhou_maids.features.smarter.state;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class MaidSmarterState {
    /**
     * smarter 激活状态（per-maid 持久化）。语义：agent 是否激活（真正接管、抑制原版 AI）。
     * <p>
     * <b>语义变迁</b>：原为"用户 UI 开关"（PossessionPanel 的"启用 Smarter"开关控制）；
     * 现改为"agent 激活状态"——由 {@code SmarterClientService} 检测 agent
     * {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.IAgent#isActive()}
     * 边界变化时 sync 写入（经 ServerboundSetSmarterModePacket）。NBT key 保留兼容旧存档。
     * <p>
     * 服务端 {@code MobServerAiStepSuppressMixin} 读此值决定是否 cancel serverAiStep
     * （抑制原版 AI）；{@code ServerboundActionIntentPacket}/{@code ServerboundBehaviorSyncPacket}
     * 读此值校验放行。
     */
    private static final String KEY_SMARTER_ON_POSSESSION = "SmarterOnPossession";
    private static final String KEY_BEHAVIOR = "Behavior";
    /**
     * Urana 快环最小轮间间隔（毫秒）。0=不限速（默认，全速运转）；>0=两轮间至少间隔该值。
     * per-maid 持久化，由 GUI 配置、awaken 启动时读取。
     */
    private static final String KEY_FAST_MIN_DT_MILLIS = "FastMinDtMillis";
    /**
     * Urana 慢环最小轮间间隔（毫秒）。0=不限速（默认，全速运转）；>0=两轮间至少间隔该值。
     * per-maid 持久化，由 GUI 配置、awaken 启动时读取。
     */
    private static final String KEY_SLOW_MIN_DT_MILLIS = "SlowMinDtMillis";

    private MaidSmarterState() {}

    public static boolean isEnabled(EntityMaid maid) {
        try {
            CompoundTag data = maid.getPersistentData();
            if (data.contains(SmarterTouhouMaids.MOD_ID, 10)) {
                CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
                if (modData.contains(KEY_SMARTER_ON_POSSESSION, 1)) {
                    return modData.getBoolean(KEY_SMARTER_ON_POSSESSION);
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static void setEnabled(EntityMaid maid, boolean enabled) {
        CompoundTag data = maid.getPersistentData();
        CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
        modData.putBoolean(KEY_SMARTER_ON_POSSESSION, enabled);
        data.put(SmarterTouhouMaids.MOD_ID, modData);
    }

    public static void setBehavior(EntityMaid maid, long[] behavior) {
        CompoundTag data = maid.getPersistentData();
        CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
        modData.putLongArray(KEY_BEHAVIOR, behavior);
        data.put(SmarterTouhouMaids.MOD_ID, modData);
    }

    public static long[] getBehavior(EntityMaid maid) {
        try {
            CompoundTag data = maid.getPersistentData();
            if (data.contains(SmarterTouhouMaids.MOD_ID, 10)) {
                CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
                if (modData.contains(KEY_BEHAVIOR, 12)) {
                    return modData.getLongArray(KEY_BEHAVIOR);
                }
            }
        } catch (Exception ignored) {}
        return new long[4];
    }

    /**
     * 读取该女仆的 Urana 快环最小轮间间隔（毫秒）。未设置时返回 0（不限速）。
     */
    public static long getFastMinDtMillis(EntityMaid maid) {
        try {
            CompoundTag data = maid.getPersistentData();
            if (data.contains(SmarterTouhouMaids.MOD_ID, 10)) {
                CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
                if (modData.contains(KEY_FAST_MIN_DT_MILLIS, 4)) {
                    return modData.getLong(KEY_FAST_MIN_DT_MILLIS);
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /**
     * 设置该女仆的 Urana 快环最小轮间间隔（毫秒）。
     */
    public static void setFastMinDtMillis(EntityMaid maid, long minDtMillis) {
        CompoundTag data = maid.getPersistentData();
        CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
        modData.putLong(KEY_FAST_MIN_DT_MILLIS, minDtMillis);
        data.put(SmarterTouhouMaids.MOD_ID, modData);
    }

    /**
     * 读取该女仆的 Urana 慢环最小轮间间隔（毫秒）。未设置时返回 0（不限速）。
     */
    public static long getSlowMinDtMillis(EntityMaid maid) {
        try {
            CompoundTag data = maid.getPersistentData();
            if (data.contains(SmarterTouhouMaids.MOD_ID, 10)) {
                CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
                if (modData.contains(KEY_SLOW_MIN_DT_MILLIS, 4)) {
                    return modData.getLong(KEY_SLOW_MIN_DT_MILLIS);
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /**
     * 设置该女仆的 Urana 慢环最小轮间间隔（毫秒）。
     */
    public static void setSlowMinDtMillis(EntityMaid maid, long minDtMillis) {
        CompoundTag data = maid.getPersistentData();
        CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
        modData.putLong(KEY_SLOW_MIN_DT_MILLIS, minDtMillis);
        data.put(SmarterTouhouMaids.MOD_ID, modData);
    }

    // ========== AI 模式选择（per-maid，递归层次）==========
    // AiModes 是一个 CompoundTag，key = registryId.toString()，value = 选中 entry id.toString()。
    // 支持任意层次（ai/process/nn/附属的新层），每层选择独立存储。
    // 旧存档无此 key 时 getModeId 返回 null，调用方 fallback 到 registry.getDefaultId()。
    private static final String KEY_AI_MODES = "AiModes";

    /**
     * 读取 maid 的所有 AI 模式选择（供外周组装 config）。不存在时返回空 CompoundTag（非 null）。
     */
    public static CompoundTag getAiModes(EntityMaid maid) {
        try {
            CompoundTag data = maid.getPersistentData();
            if (data.contains(SmarterTouhouMaids.MOD_ID, 10)) {
                CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
                if (modData.contains(KEY_AI_MODES, 10)) {
                    return modData.getCompound(KEY_AI_MODES);
                }
            }
        } catch (Exception ignored) {}
        return new CompoundTag();
    }

    /**
     * 读取 maid 在指定 registry 层的选中 entry id。
     * 未设置/非法时返回 null，调用方应 fallback 到该 registry 的 defaultId（旧存档兼容）。
     */
    public static ResourceLocation getModeId(EntityMaid maid, ResourceLocation registryId) {
        CompoundTag modes = getAiModes(maid);
        String key = registryId.toString();
        if (modes.contains(key, 8)) {
            return ResourceLocation.tryParse(modes.getString(key));
        }
        return null;
    }

    /**
     * 设置 maid 在指定 registry 层的选中 entry id。
     */
    public static void setModeId(EntityMaid maid, ResourceLocation registryId, ResourceLocation selectedId) {
        CompoundTag data = maid.getPersistentData();
        CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
        CompoundTag modes = modData.getCompound(KEY_AI_MODES);
        modes.putString(registryId.toString(), selectedId.toString());
        modData.put(KEY_AI_MODES, modes);
        data.put(SmarterTouhouMaids.MOD_ID, modData);
    }
}