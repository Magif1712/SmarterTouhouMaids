package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterLayerWalker;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamStore;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 持久化配置声明与便捷读取：per-maid 持久化配置（开关 + save 间隔 + 最大保留版本数）。
 * <p>
 * <b>统一参数管道</b>（真善美第2/4条）：持久化配置复用 {@link ParamOption} + {@link ParamStore}，
 * per-maid 存储（maid NBT），随存档走，网络同步。不再用全局 json——消除 global state，
 * 换无环/轮模式的 AI 也能用（"定时"是墙钟时间，不假设 AI 有"轮"概念）。
 * <p>
 * <b>独立 Provider，不挂 factory</b>（真善美第3条）：持久化配置是 smarter 体系级配置，
 * 不属于具体 agent/ai/process/nn 实现。换 agent 不改持久化配置声明。
 * <p>
 * <b>两级开关分层</b>（C10 递归）：persistence_enabled（总开关，控制终态 save）
 * → periodicSaveEnabled（子开关，控制运行中定期 save）
 * → saveIntervalSeconds（旋钮，仅子开关开时呈现）。maxRetention 归总开关管。
 * 两级嵌套是呈现模式，由 {@link PersistenceConfigPanel} 呈现层自管——本 Provider 只声明配置项。
 * <p>
 * <b>开关默认值由路径声明</b>（C8）：persistence_enabled 默认由
 * {@link SmarterLayerWalker#anyPersistable} 算（路径上任一 factory 声明 PersistableProvider=true → 默认开）。
 * 用户可在 GUI 覆盖。用 {@link ParamOption#of}（persistable 的 defaultValue 是 String 常量，无法动态）。
 * <p>
 * <b>便捷读取方法</b>：供 {@code UranaProcessFactory} / {@code SmarterClientService} 等
 * 非 GUI 消费者用。不通过 ParamOption 对象，直接读 ParamStore + 类型转换 + 默认值。
 * <p>
 * <b>ParamOption 声明 vs 便捷读取</b>：两者单一数据源（同 nbtKey + 同默认值）。
 * 声明供 GUI 数据驱动渲染控件；便捷读取供非 GUI 消费者直接取值（如 intervalProvider 在慢环线程调）。
 */
public final class PersistenceConfigProvider {

    private PersistenceConfigProvider() {
    }

    // ==================== NBT keys ====================
    static final String KEY_PERSISTENCE_ENABLED = "persistence_enabled";
    static final String KEY_PERIODIC_SAVE_ENABLED = "periodicSaveEnabled";
    static final String KEY_SAVE_INTERVAL_SECONDS = "saveIntervalSeconds";
    static final String KEY_MAX_RETENTION = "maxRetention";

    // ==================== 固定默认值（旋钮）====================
    static final String DEFAULT_PERIODIC_SAVE_ENABLED = "true";
    static final String DEFAULT_SAVE_INTERVAL_SECONDS = "30";
    static final String DEFAULT_MAX_RETENTION = "5";
    private static final int SAVE_INTERVAL_MIN = 1;
    private static final int SAVE_INTERVAL_MAX = 3600;
    private static final int RETENTION_MIN = 1;
    private static final int RETENTION_MAX = 100;
    private static final int SAVE_INTERVAL_FALLBACK = 30;
    private static final int RETENTION_FALLBACK = 5;

    // ==================== ParamOption 声明 ====================

    /**
     * 全部持久化配置项（供附属 Panel 扁平遍历用）。
     * <p>
     * 主模组 {@link PersistenceConfigPanel} 因两级开关嵌套需按固定结构获取，
     * 附属 Panel 若不需嵌套可直接遍历此列表。
     */
    public static List<ParamOption> getOptions() {
        return List.of(persistenceEnabled(), periodicSaveEnabled(), saveIntervalSeconds(), maxRetention());
    }

    /**
     * 总开关（是否启用持久化，控制终态 save）。controlHint="toggle" → CycleButton。
     * <p>
     * 用 {@link ParamOption#of}：persistable 的 defaultValue 是 String 常量，
     * 但本开关默认值需动态（pathDefault = {@link SmarterLayerWalker#anyPersistable}）。
     * currentText 读 ParamStore，无值时 fallback 到 pathDefault。
     */
    public static ParamOption persistenceEnabled() {
        return ParamOption.of(
                Component.translatable("option.smarter_touhou_maids.persistence_enabled"),
                Component.translatable("option.smarter_touhou_maids.persistence_enabled.tooltip"),
                maid -> {
                    String v = ParamStore.INSTANCE.getString(maid, KEY_PERSISTENCE_ENABLED, "");
                    if (!v.isEmpty()) return v;
                    return String.valueOf(SmarterLayerWalker.anyPersistable(maid));
                },
                (maid, text) -> ParamStore.INSTANCE.setString(maid, KEY_PERSISTENCE_ENABLED, text))
                .withControlHint("toggle");
    }

    /**
     * 子开关（定时持久化开关，控制运行中定期 save）。默认 true。controlHint="toggle"。
     * <p>
     * 与总开关正交：总开关控制终态 save（smarter 终止时），
     * 本开关控制定时 save（运行中周期性）。关闭本开关：不再定期 save，但 smarter 终止时仍 save。
     */
    public static ParamOption periodicSaveEnabled() {
        return ParamOption.persistable(
                Component.translatable("option.smarter_touhou_maids.periodic_save_enabled"),
                Component.translatable("option.smarter_touhou_maids.periodic_save_enabled.tooltip"),
                KEY_PERIODIC_SAVE_ENABLED, DEFAULT_PERIODIC_SAVE_ENABLED)
                .withControlHint("toggle");
    }

    /**
     * 保存间隔（秒，墙钟时间）。默认 30，范围 [1, 3600]。controlHint="text" → EditBox 兜底。
     * <p>
     * 单位是时间而非轮数（真善美第3条）：定时持久化的"定时"是公共墙钟时间，
     * 不假设 AI 有"轮"概念。附属可改 controlHint="slider" + controlMeta(min/max/step)。
     */
    public static ParamOption saveIntervalSeconds() {
        return ParamOption.persistable(
                Component.translatable("option.smarter_touhou_maids.save_interval_seconds"),
                Component.translatable("option.smarter_touhou_maids.save_interval_seconds.tooltip"),
                KEY_SAVE_INTERVAL_SECONDS, DEFAULT_SAVE_INTERVAL_SECONDS,
                (maid, text) -> String.valueOf(parseClampInt(text, SAVE_INTERVAL_MIN, SAVE_INTERVAL_MAX, SAVE_INTERVAL_FALLBACK)))
                .withControlHint("text");
    }

    /**
     * 最大保留版本数。默认 5，范围 [1, 100]。controlHint="text" → EditBox 兜底。
     */
    public static ParamOption maxRetention() {
        return ParamOption.persistable(
                Component.translatable("option.smarter_touhou_maids.max_retention"),
                Component.translatable("option.smarter_touhou_maids.max_retention.tooltip"),
                KEY_MAX_RETENTION, DEFAULT_MAX_RETENTION,
                (maid, text) -> String.valueOf(parseClampInt(text, RETENTION_MIN, RETENTION_MAX, RETENTION_FALLBACK)))
                .withControlHint("text");
    }

    // ==================== 便捷读取（非 GUI 消费者用）====================

    /**
     * 是否启用持久化（总开关）。
     * <p>
     * ParamStore 无值时 fallback 到 pathDefault（{@link SmarterLayerWalker#anyPersistable}）。
     * 供 {@code SmarterClientService.shutdown} 判断是否终态 save。
     */
    public static boolean isPersistenceEnabled(EntityMaid maid) {
        String v = ParamStore.INSTANCE.getString(maid, KEY_PERSISTENCE_ENABLED, "");
        if (!v.isEmpty()) return Boolean.parseBoolean(v);
        return SmarterLayerWalker.anyPersistable(maid);
    }

    /**
     * 是否启用定时持久化（子开关）。默认 true。
     * <p>
     * 供 {@code UranaProcessFactory} 的 intervalProvider 判断是否定期 save。
     */
    public static boolean isPeriodicSaveEnabled(EntityMaid maid) {
        return Boolean.parseBoolean(
                ParamStore.INSTANCE.getString(maid, KEY_PERIODIC_SAVE_ENABLED, DEFAULT_PERIODIC_SAVE_ENABLED));
    }

    /**
     * 定期 save 间隔（毫秒，墙钟时间）。
     * <p>
     * 供 {@code UranaSystem} 的 intervalProvider 用。返回值已 ×1000 转毫秒。
     * 定时开关关时由调用方判断返回 0L（本方法只返回间隔值，不感知开关）。
     */
    public static long getSaveIntervalMillis(EntityMaid maid) {
        int seconds = parseClampInt(
                ParamStore.INSTANCE.getString(maid, KEY_SAVE_INTERVAL_SECONDS, DEFAULT_SAVE_INTERVAL_SECONDS),
                SAVE_INTERVAL_MIN, SAVE_INTERVAL_MAX, SAVE_INTERVAL_FALLBACK);
        return seconds * 1000L;
    }

    /**
     * 最大保留版本数。默认 5。
     * <p>
     * 供 {@code SaveSlotFactory.pruneOldVersions} 用。
     */
    public static int getMaxRetention(EntityMaid maid) {
        return parseClampInt(
                ParamStore.INSTANCE.getString(maid, KEY_MAX_RETENTION, DEFAULT_MAX_RETENTION),
                RETENTION_MIN, RETENTION_MAX, RETENTION_FALLBACK);
    }

    // ==================== 内部 ====================

    private static int parseClampInt(String text, int min, int max, int fallback) {
        try {
            int v = Integer.parseInt(text.trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
