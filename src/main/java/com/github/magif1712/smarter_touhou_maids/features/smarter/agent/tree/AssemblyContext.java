package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

import java.util.Map;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.jetbrains.annotations.Nullable;

/**
 * 单次组装的上下文（工厂自驱组装的取料口）。
 * <p>
 * 持有：女仆实体（per-maid 参数经 ParamStore 自取的入口）、持久化槽位（下层 load）、
 * 以及本 Branch 已解析的具名子实例（由 Resolver 按 Branch.children 声明预先解析）。
 * <p>
 * <b>缓存语义（硬约束3）</b>：子实例缓存键 = (nodeId, branchId)，且缓存对象
 * 存活于<b>单次组装</b>内（本对象随组装创建、随组装丢弃）——Selection / maid / slot
 * 在一次组装内不变，故同键必同值，不会跨组装污染外延。共享子插槽在单次组装内只构造一次。
 */
public final class AssemblyContext {
    @Nullable
    private final EntityMaid maid;
    @Nullable
    private final SaveSlot saveSlot;
    private final Map<String, Object> resolvedChildren;

    public AssemblyContext(@Nullable EntityMaid maid, @Nullable SaveSlot saveSlot,
                           Map<String, Object> resolvedChildren) {
        this.maid = maid;
        this.saveSlot = saveSlot;
        this.resolvedChildren = resolvedChildren;
    }

    @Nullable
    public EntityMaid maid() {
        return maid;
    }

    @Nullable
    public SaveSlot saveSlot() {
        return saveSlot;
    }

    /**
     * 取本 Branch 声明过的具名子实例（Resolver 已按声明解析）。
     * 未声明的名取不到——工厂只能感知自己声明的 children（外层不感知下层组装细节）。
     */
    @Nullable
    public Object child(String name) {
        return resolvedChildren.get(name);
    }

    /**
     * 带类型取子实例。类型不匹配在 freeze 期已被图校验排除，此处为受控强转。
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <C> C child(String name, Class<C> type) {
        return (C) resolvedChildren.get(name);
    }
}
