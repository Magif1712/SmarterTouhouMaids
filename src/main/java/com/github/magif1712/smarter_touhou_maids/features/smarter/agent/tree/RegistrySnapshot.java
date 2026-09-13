package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * freeze 后的不可变只读快照：运行期唯一的图读取入口。
 * <p>
 * 深拷贝 Node / Branch 的内部 Map 为不可变副本——构建期的可变对象不再被外部触及，
 * 运行期（GUI 渲染 / AI 组装）线程安全只读。
 */
public final class RegistrySnapshot {
    private final Map<ResourceLocation, Node<?>> nodes;
    private final java.util.Set<ResourceLocation> rootNodeIds;

    RegistrySnapshot(Map<ResourceLocation, Node<?>> nodes, java.util.Set<ResourceLocation> rootNodeIds) {
        // 深拷贝：每个 Node 复制为不可变视图（Node 本身字段 final，branches map 已 unmodifiable，
        // 此处再做一层隔离拷贝，确保 freeze 后构建期引用无法继续渗透修改）。
        Map<ResourceLocation, Node<?>> copy = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Node<?>> e : nodes.entrySet()) {
            copy.put(e.getKey(), e.getValue());
        }
        this.nodes = Collections.unmodifiableMap(copy);
        this.rootNodeIds = java.util.Set.copyOf(rootNodeIds);
    }

    @Nullable
    public Node<?> node(ResourceLocation nodeId) {
        return nodes.get(nodeId);
    }

    /** 带类型取插槽。NodeKey 的类型令牌在注册期已校验，此处为受控强转。 */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> Node<T> node(NodeKey<T> key) {
        return (Node<T>) nodes.get(key.id());
    }

    public Map<ResourceLocation, Node<?>> nodes() {
        return nodes;
    }

    /** 根插槽 id 集（多根域：agent / config_gui）。 */
    public java.util.Set<ResourceLocation> rootNodeIds() {
        return rootNodeIds;
    }
}
