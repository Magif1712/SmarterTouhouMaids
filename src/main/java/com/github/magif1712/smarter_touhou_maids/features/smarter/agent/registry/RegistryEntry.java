package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 注册表条目：一个可选模式的描述符（真善美第3条：把"可选模式"这个不实在的约束，
 * 实在化为有签名的 Entry 对象）。
 * <p>
 * 每个 Entry 描述：我是谁（id）、怎么显示（displayNameKey，i18n key）、怎么造（factory）、
 * 选了我之后还需要进一步选哪个下层 registry（subRegistryId）。
 * <p>
 * <b>subRegistryId 与递归层次</b>（真善美第2条：上层决定下层）：
 * <ul>
 *   <li>非 null：选了本 entry 后，GUI 递归展开 subRegistryId 指向的下层 registry 的选择按钮。
 *       例如 ProcessAiSystem 的 entry.subRegistryId = process registry，表示"选了流程型 ai 后
 *       还要选具体流程系统"。</li>
 *   <li>null：叶子，无下层。例如 BnnNeuralNetwork 的 entry.subRegistryId = null（nn 之下无选择）；
 *       纯规则 ai 的 entry.subRegistryId = null（不需要 process/nn）。</li>
 * </ul>
 * 这使 GUI 能兼容任意层次：附属模组加新 entry 时声明自己的 subRegistryId，GUI 自动递归展开，
 * 层次数量不限，GUI 代码零改动。
 *
 * @param <T> factory 类型（AiFactory / ProcessFactory / NnFactory）
 */
public class RegistryEntry<T> {
    private final ResourceLocation id;
    private final String displayNameKey;
    private final T factory;
    @Nullable
    private final ResourceLocation subRegistryId;

    public RegistryEntry(ResourceLocation id, String displayNameKey, T factory, @Nullable ResourceLocation subRegistryId) {
        this.id = id;
        this.displayNameKey = displayNameKey;
        this.factory = factory;
        this.subRegistryId = subRegistryId;
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getDisplayNameKey() {
        return displayNameKey;
    }

    public T getFactory() {
        return factory;
    }

    @Nullable
    public ResourceLocation getSubRegistryId() {
        return subRegistryId;
    }
}
