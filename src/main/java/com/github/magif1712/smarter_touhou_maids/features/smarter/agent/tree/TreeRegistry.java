package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 概念树的构建期注册表（可变）——对应旧体系的 RegistryManager，但显式持有 Node 图。
 * <p>
 * <b>时序锁死（硬约束6）</b>：mod loading 期可写；{@link #freeze()} 后任何注册调用
 * 抛 {@link IllegalStateException}。freeze 做全图校验（见 GraphValidator）并产出
 * 不可变 {@link RegistrySnapshot}，运行期只读快照。
 * <p>
 * 附属模组经 RegistryCollectEvent 拿到本表注册自己的 Node / Branch；
 * 主模组不 import 附属类、不硬编码附属 modid——识别仅靠图中出现的附属命名空间。
 */
public final class TreeRegistry {
    private final Map<ResourceLocation, Node<?>> nodes = new LinkedHashMap<>();
    private boolean frozen;

    /**
     * 注册（或取回）一个插槽。同 id 重复注册时校验类型令牌一致后返回已有 Node。
     */
    @SuppressWarnings("unchecked")
    public <T> Node<T> node(NodeKey<T> key) {
        checkWritable("node");
        Node<?> existing = nodes.get(key.id());
        if (existing != null) {
            if (!existing.key().type().equals(key.type())) {
                throw new IllegalArgumentException("NodeKey " + key + " 类型令牌冲突: "
                        + existing.key().type().getName() + " vs " + key.type().getName());
            }
            return (Node<T>) existing;
        }
        Node<T> node = new Node<>(key);
        nodes.put(key.id(), node);
        return node;
    }

    @Nullable
    public Node<?> get(ResourceLocation nodeId) {
        return nodes.get(nodeId);
    }

    public Map<ResourceLocation, Node<?>> nodes() {
        return nodes;
    }

    public boolean isFrozen() {
        return frozen;
    }

    /**
     * 冻结：全图校验（GraphValidator）→ 生成不可变快照。校验失败抛异常并附完整错误列表。
     *
     * @param rootNodeIds 根插槽 id 集（可达性检查起点；本模组为 agent + config_gui 两个域根，
     *                    config_gui 域仅客户端存在，调用方按存在性动态给出）
     */
    public RegistrySnapshot freeze(java.util.Set<ResourceLocation> rootNodeIds /*->*/) {
        checkWritable("freeze");
        java.util.List<String> errors = new java.util.ArrayList<>();
        GraphValidator.validate(nodes, rootNodeIds, /*->*/ errors);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("概念树 freeze 校验失败:\n - " + String.join("\n - ", errors));
        }
        frozen = true;
        return new RegistrySnapshot(nodes, rootNodeIds);
    }

    private void checkWritable(String op) {
        if (frozen) {
            throw new IllegalStateException("概念树已 freeze，禁止再注册（" + op + "）");
        }
    }
}
