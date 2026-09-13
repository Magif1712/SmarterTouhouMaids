package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 运行时选择：每个插槽选了哪个分支（nodeId → branchId）。
 * <p>
 * 对应旧体系 NBT 的 {@code AiModes} CompoundTag（key=registryId, value=entryId）——
 * 格式同构，旧存档零迁移。
 * <p>
 * <b>宽容性</b>：允许残留无关条目（指向不存在 Node / Branch 的条目在解析时自然被忽略、
 * 回退默认）——附属模组卸载后不崩溃。不可变。
 */
public final class Selection {
    private final Map<ResourceLocation, ResourceLocation> choices;

    public Selection(Map<ResourceLocation, ResourceLocation> choices) {
        this.choices = Collections.unmodifiableMap(new LinkedHashMap<>(choices));
    }

    public static Selection empty() {
        return new Selection(Map.of());
    }

    /** 该插槽的选中分支；未设置返回 null（由 Resolver 回退默认）。 */
    @Nullable
    public ResourceLocation branchOf(NodeKey<?> nodeKey) {
        return choices.get(nodeKey.id());
    }

    /** 同 {@link #branchOf(NodeKey)}，按裸 id 取（GUI/持久化层手头只有 id 时用）。 */
    @Nullable
    public ResourceLocation branchOf(ResourceLocation nodeId) {
        return choices.get(nodeId);
    }

    /** 全部选择（持久化 / GUI 用）。 */
    public Map<ResourceLocation, ResourceLocation> choices() {
        return choices;
    }

    /** 派生一个新 Selection（覆盖一条选择）。 */
    public Selection with(NodeKey<?> nodeKey, ResourceLocation branchId) {
        Map<ResourceLocation, ResourceLocation> copy = new LinkedHashMap<>(choices);
        copy.put(nodeKey.id(), branchId);
        return new Selection(copy);
    }
}
