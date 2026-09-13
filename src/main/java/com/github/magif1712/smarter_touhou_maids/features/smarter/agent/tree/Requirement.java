package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

import net.minecraft.resources.ResourceLocation;

/**
 * 分支间兼容性契约（推论2 的实在化：选择空间的合法约束用类型校验表达）。
 * <p>
 * 语义：挂载本 Requirement 的 Branch 被选中时，目标插槽（targetNodeId）所选分支的
 * 产出类型必须可被 requiredType 接受（{@code requiredType.isAssignableFrom(产出类型)}）。
 * <p>
 * 例：{@code urana_original} 分支声明 {@code requires(SENSOR, IPushEncodedSensor.class)}——
 * 旧流程只兼容"采集编码合一推模型"的感受器。附属模组定义自己的契约接口并声明 requires，
 * 即可无缝接入同一套约束机制，主模组零感知、零硬编码。
 */
public final class Requirement {
    private final ResourceLocation targetNodeId;
    private final Class<?> requiredType;

    public Requirement(ResourceLocation targetNodeId, Class<?> requiredType) {
        this.targetNodeId = targetNodeId;
        this.requiredType = requiredType;
    }

    public ResourceLocation targetNodeId() {
        return targetNodeId;
    }

    public Class<?> requiredType() {
        return requiredType;
    }

    /** 目标分支的产出类型是否满足本契约。 */
    public boolean isSatisfiedBy(Class<?> producedType) {
        return requiredType.isAssignableFrom(producedType);
    }

    @Override
    public String toString() {
        return "requires(" + targetNodeId + " -> " + requiredType.getSimpleName() + ")";
    }
}
