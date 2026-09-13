package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

/**
 * Branch 的展示与溯源元数据。
 * <p>
 * 显示名用 i18n key（与旧 RegistryEntry.displayNameKey 一致）；
 * {@code sourceModId} 默认取 branch id 的 namespace——主模组"识别"附属模组的唯一方式
 * 即注册图中出现了附属命名空间的 Branch / Node，此处把它实在化为字段。
 */
public final class Meta {
    private final String displayNameKey;
    private final int order;
    private final String sourceModId;

    public Meta(String displayNameKey, int order, String sourceModId) {
        this.displayNameKey = displayNameKey;
        this.order = order;
        this.sourceModId = sourceModId;
    }

    public String displayNameKey() {
        return displayNameKey;
    }

    /** GUI 排序权重（小在前）。 */
    public int order() {
        return order;
    }

    /** 来源 modid（= branch id 的 namespace）。 */
    public String sourceModId() {
        return sourceModId;
    }
}
