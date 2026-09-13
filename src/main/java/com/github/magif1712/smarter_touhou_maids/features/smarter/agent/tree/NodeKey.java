package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

import net.minecraft.resources.ResourceLocation;

/**
 * 概念树中的<b>插槽键</b>：标识一个"角色/插槽"（如 agent / ai / process / mapper / nn）。
 * <p>
 * 对应旧体系的 registryId——<b>id 与旧 registryId 保持一致</b>，使 NBT 持久化格式
 * （key=nodeId, value=branchId）零迁移兼容旧存档。
 * <p>
 * {@code type} 是类型令牌：freeze 时校验每个挂到本插槽的 Branch 的工厂产出类型
 * 可被 {@code type} 接受；运行时解析据此做受控强转，消灭散落的裸强转。
 * <p>
 * 相等性只按 {@code id}（类型令牌是注册期校验元数据，不参与相等性）。
 *
 * @param <T> 本插槽产出实例的类型（如 IAgent / IAiSystem / IProcessSystem）
 */
public final class NodeKey<T> {
    private final ResourceLocation id;
    private final Class<T> type;

    public NodeKey(ResourceLocation id, Class<T> type) {
        this.id = id;
        this.type = type;
    }

    public ResourceLocation id() {
        return id;
    }

    public Class<T> type() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NodeKey<?> other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
