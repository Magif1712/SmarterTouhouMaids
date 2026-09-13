package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/**
 * 运行时解析器：Selection + RegistrySnapshot → 实例树。
 * <p>
 * 算法（与你的规格一致）：
 * <pre>
 * resolve(node, selection, ctx):
 *     branch = node.branches[selection[node]] ?: node.branches[node.defaultBranch]
 *     children = { name : resolve(childNode) for (name, childNode) in branch.children }
 *     return branch.factory.create(ctx + children)
 * </pre>
 * 关键性质：
 * <ul>
 *   <li><b>默认回退在 resolve 时做</b>（不在 NBT 加载时替换）——未知/未设置的分支 id
 *       自然落到 defaultBranch，旧存档与附属卸载均不崩溃。</li>
 *   <li><b>只解析当前 Branch 声明的 children</b>——未声明的子插槽根本不解析，
 *       Selection 里残留的无关条目无害。</li>
 *   <li><b>单次组装缓存</b>：(nodeId, branchId) 键缓存活在单次组装的 session 里
 *       （硬约束3：缓存随 AssemblyContext 创建/丢弃，Selection 不变即无污染）。</li>
 * </ul>
 */
public final class Resolver {
    private Resolver() {
    }

    /**
     * 从根插槽解析整棵实例树。
     *
     * @param maid      女仆实体（可空，null 时全走默认分支）
     * @param saveSlot  持久化槽位（可空）
     * @param selection 运行时选择（未知条目无害）
     * @param out       出参：根实例
     */
    public static <T> void resolve(RegistrySnapshot snapshot, NodeKey<T> rootKey, Selection selection,
                                   @org.jetbrains.annotations.Nullable com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid,
                                   @org.jetbrains.annotations.Nullable com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot saveSlot,
                                   /*->*/ OutSlot<T> out) {
        Session session = new Session(snapshot, selection, maid, saveSlot);
        // 运行期契约告警：Selection 显式固定了不兼容组合时提示（不阻断——d 是显式入参，
        // 约束由 GUI（selectableBranches）与 freeze（全默认可满足）负责，此处仅诊断）。
        if (!ConstraintSolver.isSatisfiable(snapshot.nodes()::get, rootKey, selection)) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "[ConceptTree] 当前 Selection 不满足兼容性契约，按现状解析（GUI 本应拦截）: {}",
                    selection.choices());
        }
        out.set(resolveNode(session, rootKey));
    }

    private static <T> T resolveNode(Session session, NodeKey<T> nodeKey) {
        RegistrySnapshot snapshot = session.snapshot;
        Node<T> node = snapshot.node(nodeKey);
        if (node == null) {
            throw new IllegalStateException("插槽未注册: " + nodeKey);
        }

        // 分支选择：Selection → 默认回退（回退在此收敛，不在 NBT 加载时）
        ResourceLocation branchId = session.selection.branchOf(nodeKey);
        Branch<T> branch = branchId != null ? node.branch(branchId) : null;
        if (branch == null) {
            ResourceLocation def = node.defaultBranch();
            branch = def != null ? node.branch(def) : null;
        }
        if (branch == null) {
            throw new IllegalStateException("插槽 " + nodeKey + " 无可用分支（freeze 校验本应排除此情况）");
        }

        // 单次组装缓存：同 (nodeId, branchId) 共享子插槽只构造一次
        String cacheKey = nodeKey.id() + "@" + branch.id();
        Object cached = session.cache.get(cacheKey);
        if (cached != null) {
            return nodeKey.type().cast(cached);
        }

        // 只解析当前 Branch 声明的 children
        Map<String, Object> children = new LinkedHashMap<>();
        for (Map.Entry<String, NodeKey<?>> childEntry : branch.children().entrySet()) {
            children.put(childEntry.getKey(), resolveChild(session, childEntry.getValue()));
        }

        AssemblyContext ctx = new AssemblyContext(session.maid, session.saveSlot, children);
        OutSlot<T> slot = new OutSlot<>();
        branch.factory().create(ctx, /*->*/ slot);
        T instance = slot.get();
        session.cache.put(cacheKey, instance);
        return instance;
    }

    private static <C> C resolveChild(Session session, NodeKey<C> childKey) {
        return resolveNode(session, childKey);
    }

    /**
     * 单次组装会话：缓存作用域 = 一次根解析（硬约束3：键含 nodeId+branchId，
     * 且 session 内 Selection/maid/saveSlot 不变，故同键必同值，无跨组装污染）。
     */
    private static final class Session {
        final RegistrySnapshot snapshot;
        final Selection selection;
        final com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid;
        final com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot saveSlot;
        final Map<String, Object> cache = new LinkedHashMap<>();

        Session(RegistrySnapshot snapshot, Selection selection,
                com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid,
                com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot saveSlot) {
            this.snapshot = snapshot;
            this.selection = selection;
            this.maid = maid;
            this.saveSlot = saveSlot;
        }
    }
}
