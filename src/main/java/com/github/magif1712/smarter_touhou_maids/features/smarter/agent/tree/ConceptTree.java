package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 概念树的模组级持有器：加载期可写（{@link #builder()}），freeze 后运行期只读。
 * <p>
 * 生命周期（时序锁死，硬约束6）：
 * <ol>
 *   <li>mod loading 期：各层注册处（@EventBusSubscriber / 附属的 RegistryCollectEvent 监听者）
 *       经 {@link #builder()} 向共享 TreeRegistry 注册自己的 Node / Branch。</li>
 *   <li>FMLLoadCompleteEvent：主类调 {@link #freeze}，全图校验后生成快照，此后 builder 拒写。</li>
 *   <li>运行期：只读 {@link #snapshot()}（GUI 递归展示 / Resolver 组装 / ConstraintSolver 过滤）。</li>
 * </ol>
 */
public final class ConceptTree {
    private static final TreeRegistry BUILDER = new TreeRegistry();
    private static volatile RegistrySnapshot snapshot;

    private ConceptTree() {
    }

    /** 构建期注册表（freeze 后抛异常）。 */
    public static TreeRegistry builder() {
        if (snapshot != null) {
            throw new IllegalStateException("概念树已 freeze，禁止再注册");
        }
        return BUILDER;
    }

    /**
     * 冻结并发布快照。
     *
     * @param rootNodeIds 根插槽 id 集（agent 域 + config_gui 域；config_gui 仅客户端注册，
     *                    调用方按 builder 中实际存在性给出）
     */
    public static void freeze(Set<ResourceLocation> rootNodeIds /*->*/) {
        if (snapshot != null) {
            throw new IllegalStateException("概念树快照已存在，禁止重复 freeze");
        }
        snapshot = BUILDER.freeze(rootNodeIds /*->*/);
    }

    /** 运行期只读快照；freeze 前返回 null。 */
    @Nullable
    public static RegistrySnapshot snapshot() {
        return snapshot;
    }
}
