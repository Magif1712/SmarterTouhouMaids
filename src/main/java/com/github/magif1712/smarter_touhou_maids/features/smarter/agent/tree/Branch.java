package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 概念树中的<b>具体实现</b>（对应旧体系的 RegistryEntry）。
 * <p>
 * 一个 Branch 描述：我是谁（id）、怎么显示（meta）、怎么造（factory）、
 * 选了我之后我内部还需要哪些子插槽（children，具名）。
 * <p>
 * <b>children 挂在 Branch 上，不挂在 Node 上</b>：同一 Node 的不同 Branch 可以有完全不同的
 * 子插槽结构（原初代理无 mapper、新版代理有 mapper、非流程 AI 有 policy/memory——异构正常）。
 * <b>多归属</b>：同一 factory 实例可被多个 Branch 引用，同一 Branch 结构可注册到多个 Node 下。
 * <p>
 * 可变对象，仅在构建期（freeze 前）写入；freeze 后由 RegistrySnapshot 深拷为不可变视图。
 *
 * @param <T> 产出实例类型
 */
public final class Branch<T> {
    private final ResourceLocation id;
    private final Factory<T> factory;
    private final Map<String, NodeKey<?>> children = new LinkedHashMap<>();
    /** 兼容性契约：本分支被选中时，目标插槽的所选分支产出类型必须满足各 Requirement。 */
    private final java.util.List<Requirement> requires = new java.util.ArrayList<>();
    private Meta meta;

    public Branch(ResourceLocation id, Factory<T> factory, Meta meta) {
        this.id = id;
        this.factory = factory;
        this.meta = meta;
    }

    public ResourceLocation id() {
        return id;
    }

    public Factory<T> factory() {
        return factory;
    }

    public Meta meta() {
        return meta;
    }

    public void meta(Meta meta) {
        this.meta = meta;
    }

    /**
     * 声明一个具名子插槽。重复声明同名 child 抛异常（构建期 fail-fast）。
     */
    public void addChild(String name, NodeKey<?> childKey) {
        if (children.putIfAbsent(name, childKey) != null) {
            throw new IllegalStateException("Branch " + id + " 重复声明子插槽: " + name);
        }
    }

    /** 具名子插槽（构建期视图；freeze 后由快照提供不可变副本）。 */
    public Map<String, NodeKey<?>> children() {
        return Collections.unmodifiableMap(children);
    }

    /** 按名取子插槽，未声明返回 null。 */
    @Nullable
    public NodeKey<?> child(String name) {
        return children.get(name);
    }

    /**
     * 声明一条兼容性契约：本分支被选中时，targetNodeId 插槽所选分支的产出类型
     * 必须是 requiredType 的子类型。目标插槽不限于本分支的 children（可为同树任意插槽，
     * 如 sensor 之于 process——兄弟插槽间的封装约束）。
     */
    public void addRequirement(ResourceLocation targetNodeId, Class<?> requiredType) {
        requires.add(new Requirement(targetNodeId, requiredType));
    }

    /** 本分支声明的全部兼容性契约。 */
    public java.util.List<Requirement> requires() {
        return Collections.unmodifiableList(requires);
    }
}
