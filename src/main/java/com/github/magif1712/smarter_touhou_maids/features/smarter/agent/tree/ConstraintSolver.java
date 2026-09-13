package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 兼容性约束求解器（推论2 的图算法核心：把"无法选到不兼容组合"实在化为
 * 可满足性判定，供 GUI 过滤与 freeze 校验使用；主模组与附属共用，零硬编码）。
 * <p>
 * 语义约定：
 * <ul>
 *   <li><b>fixed</b> = 用户显式选择（Selection）。指向不存在分支的条目视为未设置
 *       （与 {@link Resolver} 的回退语义一致）。</li>
 *   <li><b>自由插槽</b>（未被 fixed 固定）= 存在性语义：存在某个分支使整棵树可满足即可。
 *       这样"先选 process=urana_original 再选 sensor=legacy"或反过来，两端都各自可选，
 *       但不兼容组合无法同时固定——无论从哪端先选。</li>
 *   <li>活动树 = 沿所选分支的 children 边展开到的插槽集合；未激活插槽上的 fixed 条目
 *       是无害残留，不参与判定。</li>
 *   <li>Requirement 的目标插槽不在活动树内 → 不满足（fail-closed：
 *       防止"需要 sensor 的分支被装到没有 sensor 插槽的组合里"）。</li>
 * </ul>
 * 图规模小（插槽数十量级），回溯搜索不做剪枝优化，保持最低描述长度。
 */
public final class ConstraintSolver {
    private ConstraintSolver() {
    }

    /**
     * 整棵树在给定显式选择下是否可满足（存在自由插槽的某个完成使所有契约成立）。
     *
     * @param nodeById 插槽查找（TreeRegistry 或 RegistrySnapshot 的视图）
     */
    public static boolean isSatisfiable(Function<ResourceLocation, Node<?>> nodeById,
                                        NodeKey<?> root, Selection fixed) {
        java.util.Deque<NodeKey<?>> frontier = new java.util.ArrayDeque<>();
        frontier.add(root);
        return solve(nodeById, fixed, new LinkedHashMap<>(), frontier);
    }

    /**
     * 求解并给出见证赋值（GUI 级联修正用：用户选了一个分支后，把与之冲突的其它已存选择
     * 重置为见证值——上层选的是 Ext(D) 的合法元素，见证把自由变量补全为合法组合）。
     * <p>
     * 候选顺序（最小变动感）：fixed 已固定的分支 → 插槽默认分支 → 其余按注册序。
     *
     * @param outAssignment 出参：成功时填入活动树全部插槽的 (nodeId → branchId)
     * @return 是否可满足
     */
    public static boolean solveAssignment(Function<ResourceLocation, Node<?>> nodeById,
                                          NodeKey<?> root, Selection fixed,
                                          /*->*/ Map<ResourceLocation, ResourceLocation> outAssignment) {
        Map<ResourceLocation, Branch<?>> assignment = new LinkedHashMap<>();
        java.util.Deque<NodeKey<?>> frontier = new java.util.ArrayDeque<>();
        frontier.add(root);
        if (!solve(nodeById, fixed, assignment, frontier)) {
            return false;
        }
        assignment.forEach((nodeId, branch) -> outAssignment.put(nodeId, branch.id()));
        return true;
    }

    /**
     * GUI 过滤：目标插槽中"代入后整体仍可满足"的分支集合（推论2：上层只选 Ext(D) 内元素）。
     * <p>
     * <b>fixed 只保留祖先链</b>（bug 修复：兄弟插槽的已固化条目——如级联写入的 sensor——
     * 不得锁死目标插槽的选项，否则"切到旧流程后无法切回新流程"死锁）。
     * 祖先链 = 根到目标插槽沿当前生效分支的路径（决定目标是否在场的结构因素）；
     * 兄弟/后代条目视为自由变量（应用选择时由 solveAssignment 见证级联修复）。
     * 不在当前活动树内的插槽，其全部分支视为可选（fixed 条目属无害残留）。
     */
    public static Set<ResourceLocation> selectableBranches(Function<ResourceLocation, Node<?>> nodeById,
                                                           NodeKey<?> root, NodeKey<?> target,
                                                           Selection fixed,
                                                           /*->*/ Set<ResourceLocation> out) {
        Node<?> targetNode = nodeById.apply(target.id());
        if (targetNode == null) {
            return out;
        }
        Selection ancestorFixed = ancestorFixedSelection(nodeById, root, target, fixed /*->*/);
        for (Branch<?> branch : targetNode.branches().values()) {
            Selection fixed2 = ancestorFixed.with(target, branch.id());
            if (isSatisfiable(nodeById, root, fixed2)) {
                out.add(branch.id());
            }
        }
        return out;
    }

    /**
     * 构造"仅含目标插槽祖先链固定项"的 Selection。
     * 祖先链沿当前生效分支（fixed 选中且存在 → 用之，否则默认分支）从根走到 target；
     * target 不在活动树内时返回空 Selection（全自由）。
     */
    public static Selection ancestorFixedSelection(Function<ResourceLocation, Node<?>> nodeById,
                                                   NodeKey<?> root, NodeKey<?> target, Selection fixed /*->*/) {
        Set<ResourceLocation> ancestors = new LinkedHashSet<>();
        collectAncestors(nodeById, root, target, fixed, ancestors /*->*/);
        Map<ResourceLocation, ResourceLocation> map = new LinkedHashMap<>();
        for (ResourceLocation id : ancestors) {
            ResourceLocation branchId = fixed.branchOf(id);
            if (branchId != null) {
                map.put(id, branchId);
            }
        }
        return new Selection(map);
    }

    /** DFS 收集根到 target 的祖先插槽 id（沿当前生效分支）。命中返回 true。 */
    private static boolean collectAncestors(Function<ResourceLocation, Node<?>> nodeById,
                                            NodeKey<?> current, NodeKey<?> target, Selection fixed,
                                            /*->*/ Set<ResourceLocation> out) {
        if (current.id().equals(target.id())) {
            return true;
        }
        Node<?> node = nodeById.apply(current.id());
        if (node == null) {
            return false;
        }
        // 当前生效分支：fixed 选中且存在 → 用之，否则默认分支
        ResourceLocation branchId = fixed.branchOf(current);
        Branch<?> branch = branchId != null ? node.branch(branchId) : null;
        if (branch == null) {
            ResourceLocation def = node.defaultBranch();
            branch = def != null ? node.branch(def) : null;
        }
        if (branch == null) {
            return false;
        }
        for (NodeKey<?> childKey : branch.children().values()) {
            if (collectAncestors(nodeById, childKey, target, fixed, out)) {
                out.add(current.id());
                return true;
            }
        }
        return false;
    }

    /**
     * freeze 校验用：全默认路径（每插槽取 defaultBranch）必须可满足——保证旧存档/未设置
     * 回退到合法组合。返回 null 表示可满足；否则返回第一条不满足的原因描述。
     */
    @Nullable
    public static String checkDefaultsSatisfiable(Function<ResourceLocation, Node<?>> nodeById,
                                                  NodeKey<?> root) {
        Map<ResourceLocation, Branch<?>> assignment = new LinkedHashMap<>();
        if (!walkDefaults(nodeById, root, assignment, new LinkedHashSet<>())) {
            return "全默认路径无法完整解析（某插槽无默认分支）";
        }
        return firstViolatedRequirement(assignment);
    }

    // ==================== 内部 ====================

    /**
     * 前沿队列式全回溯搜索（完备）。
     * <p>
     * 为什么不按子树递归：契约可以横跨兄弟插槽（如 process 的 requires 指向 sensor），
     * 子树逐个求解会先提交先序兄弟、后序失败时无法回头换先序兄弟的候选（不完备）。
     * 前沿式搜索把"活动插槽"看作统一的待赋值队列，任意插槽失败都可回溯到任意已赋值插槽
     * 的下一候选——对兄弟间契约完备。
     *
     * @param frontier 已激活但未赋值的插槽队列（分支选中即激活其 children）
     */
    private static boolean solve(Function<ResourceLocation, Node<?>> nodeById,
                                 Selection fixed,
                                 Map<ResourceLocation, Branch<?>> assignment,
                                 java.util.Deque<NodeKey<?>> frontier) {
        if (frontier.isEmpty()) {
            // 完整赋值：全量契约检查（目标缺席 = 不满足，fail-closed）
            return firstViolatedRequirement(assignment) == null;
        }
        NodeKey<?> nodeKey = frontier.removeLast();
        if (assignment.containsKey(nodeKey.id())) {
            // 共享子插槽（多个分支指向同一插槽）：已赋值，直接推进
            boolean ok = solve(nodeById, fixed, assignment, frontier);
            frontier.addLast(nodeKey);
            return ok;
        }
        Node<?> node = nodeById.apply(nodeKey.id());
        if (node == null) {
            frontier.addLast(nodeKey);
            return false;
        }
        for (Branch<?> branch : candidatesOf(node, fixed, nodeKey)) {
            assignment.put(nodeKey.id(), branch);
            if (localReqsOk(assignment, nodeKey, branch)) {
                int added = 0;
                for (NodeKey<?> childKey : branch.children().values()) {
                    if (!assignment.containsKey(childKey.id())) {
                        frontier.addLast(childKey);
                        added++;
                    }
                }
                if (solve(nodeById, fixed, assignment, frontier)) {
                    return true;
                }
                for (int i = 0; i < added; i++) {
                    frontier.removeLast();
                }
            }
            assignment.remove(nodeKey.id());
        }
        frontier.addLast(nodeKey);
        return false;
    }

    /** 候选（最小变动感）：fixed 固定且存在 → 它自己；否则默认分支优先，其余按注册序。 */
    private static java.util.List<Branch<?>> candidatesOf(Node<?> node, Selection fixed, NodeKey<?> nodeKey) {
        java.util.List<Branch<?>> candidates = new java.util.ArrayList<>();
        ResourceLocation fixedBranchId = fixed.branchOf(nodeKey);
        Branch<?> fixedBranch = fixedBranchId != null ? node.branch(fixedBranchId) : null;
        if (fixedBranch != null) {
            candidates.add(fixedBranch);
            return candidates;
        }
        ResourceLocation defId = node.defaultBranch();
        Branch<?> defBranch = defId != null ? node.branch(defId) : null;
        if (defBranch != null) {
            candidates.add(defBranch);
        }
        for (Branch<?> b : node.branches().values()) {
            if (b != defBranch) {
                candidates.add(b);
            }
        }
        return candidates;
    }

    /**
     * 增量契约检查（赋值时双向）：新分支的 requires 对已赋值目标 + 已赋值分支的 requires 对本插槽。
     * 目标未赋值的延迟到完整赋值时的全量检查（fail-closed）。
     */
    private static boolean localReqsOk(Map<ResourceLocation, Branch<?>> assignment,
                                       NodeKey<?> nodeKey, Branch<?> branch) {
        Class<?> produced = branch.factory().producedType();
        for (Requirement req : branch.requires()) {
            Branch<?> target = assignment.get(req.targetNodeId());
            if (target != null && !req.isSatisfiedBy(target.factory().producedType())) {
                return false;
            }
        }
        for (Map.Entry<ResourceLocation, Branch<?>> e : assignment.entrySet()) {
            for (Requirement req : e.getValue().requires()) {
                if (req.targetNodeId().equals(nodeKey.id()) && !req.isSatisfiedBy(produced)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 沿默认分支走一遍并收集赋值。任何插槽无默认/缺失返回 false。 */
    private static boolean walkDefaults(Function<ResourceLocation, Node<?>> nodeById,
                                        NodeKey<?> nodeKey,
                                        Map<ResourceLocation, Branch<?>> assignment,
                                        Set<ResourceLocation> visiting) {
        if (!visiting.add(nodeKey.id())) {
            return true;
        }
        Node<?> node = nodeById.apply(nodeKey.id());
        if (node == null) {
            return false;
        }
        ResourceLocation defId = node.defaultBranch();
        Branch<?> branch = defId != null ? node.branch(defId) : null;
        if (branch == null) {
            return false;
        }
        assignment.put(nodeKey.id(), branch);
        for (NodeKey<?> childKey : branch.children().values()) {
            if (!walkDefaults(nodeById, childKey, assignment, visiting)) {
                return false;
            }
        }
        return true;
    }

    /** 全量契约检查：返回第一条不满足的原因，全满足返回 null。目标缺席 = 不满足。 */
    @Nullable
    private static String firstViolatedRequirement(Map<ResourceLocation, Branch<?>> assignment) {
        for (Map.Entry<ResourceLocation, Branch<?>> e : assignment.entrySet()) {
            for (Requirement req : e.getValue().requires()) {
                Branch<?> target = assignment.get(req.targetNodeId());
                if (target == null) {
                    return "分支 " + e.getValue().id() + " 的契约 " + req + " 目标插槽不在活动树内";
                }
                if (!req.isSatisfiedBy(target.factory().producedType())) {
                    return "分支 " + e.getValue().id() + " 的契约 " + req
                            + " 不被满足（实际: " + target.factory().producedType().getSimpleName() + "）";
                }
            }
        }
        return null;
    }
}
