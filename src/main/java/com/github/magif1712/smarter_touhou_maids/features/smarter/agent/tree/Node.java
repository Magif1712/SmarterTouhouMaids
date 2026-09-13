package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 概念树中的<b>插槽/角色</b>（对应旧体系的一层 Registry）。
 * <p>
 * 一个 Node 描述：我是哪个插槽（key，含类型令牌）、默认选哪个实现（defaultBranch）、
 * 可选实现集合（branches）。
 * <p>
 * 可变对象，仅在构建期写入；freeze 后由 RegistrySnapshot 提供不可变视图。
 *
 * @param <T> 插槽产出实例类型
 */
public final class Node<T> {
    private final NodeKey<T> key;
    private final Map<ResourceLocation, Branch<T>> branches = new LinkedHashMap<>();
    @Nullable
    private ResourceLocation defaultBranch;

    public Node(NodeKey<T> key) {
        this.key = key;
    }

    public NodeKey<T> key() {
        return key;
    }

    @Nullable
    public ResourceLocation defaultBranch() {
        return defaultBranch;
    }

    public void defaultBranch(ResourceLocation defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    /**
     * 挂载一个实现。同 id 重复挂载抛异常（freeze 校验项2前置到构建期 fail-fast）。
     * 类型校验：branch 工厂产出类型必须可被本插槽类型令牌接受。
     */
    public void addBranch(Branch<T> branch) {
        if (!key.type().isAssignableFrom(branch.factory().producedType())) {
            throw new IllegalArgumentException(
                    "Branch " + branch.id() + " 产出类型 " + branch.factory().producedType().getName()
                            + " 不可被插槽 " + key + " 的类型 " + key.type().getName() + " 接受");
        }
        if (branches.putIfAbsent(branch.id(), branch) != null) {
            throw new IllegalStateException("Node " + key + " 下重复挂载 Branch: " + branch.id());
        }
    }

    @Nullable
    public Branch<T> branch(ResourceLocation branchId) {
        return branches.get(branchId);
    }

    public Map<ResourceLocation, Branch<T>> branches() {
        return Collections.unmodifiableMap(branches);
    }
}
