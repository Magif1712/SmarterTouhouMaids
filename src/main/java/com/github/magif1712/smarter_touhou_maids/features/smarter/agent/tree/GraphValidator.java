package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/**
 * freeze 期全图校验器。图本质：Node → Branch → Node → … 的 DAG。
 * <p>
 * 校验项：
 * <ol>
 *   <li>每个 Node 至少有一个 Branch 且 defaultBranch 存在（freeze 后解析必有退路）。</li>
 *   <li>每个 Branch 的 children 引用的 NodeKey 存在。</li>
 *   <li>类型匹配：child 引用目标 Node 时，目标插槽类型由引用方语境自行保证
 *       （Branch 挂入 Node 时已强制 isAssignableFrom，此处复核 children 指向的 Node 存在即可）。</li>
 *   <li>环检测：DFS 三色标记，沿 Branch.children 边遍历。</li>
 *   <li>从根 Node 出发的可达性：不可达 Node 报错（防止注册了却永远挂不进图的死分支）。</li>
 * </ol>
 * Node/branch id 唯一性已在构建期（TreeRegistry.node / Node.addBranch）fail-fast，
 * 此处不重复。所有错误收集进 out 列表（DPS 出参），由调用方决定是否抛异常。
 */
public final class GraphValidator {
    private GraphValidator() {
    }

    public static void validate(Map<ResourceLocation, Node<?>> nodes, Set<ResourceLocation> rootNodeIds,
                                /*->*/ List<String> outErrors) {
        // 1+2：defaultBranch / children 引用存在性
        for (Node<?> node : nodes.values()) {
            if (node.branches().isEmpty()) {
                outErrors.add("Node " + node.key() + " 没有任何 Branch");
                continue;
            }
            ResourceLocation def = node.defaultBranch();
            if (def == null) {
                outErrors.add("Node " + node.key() + " 未设置 defaultBranch");
            } else if (node.branch(def) == null) {
                outErrors.add("Node " + node.key() + " 的 defaultBranch " + def + " 不存在");
            }
            for (Branch<?> branch : node.branches().values()) {
                for (Map.Entry<String, NodeKey<?>> child : branch.children().entrySet()) {
                    if (!nodes.containsKey(child.getValue().id())) {
                        outErrors.add("Branch " + branch.id() + "（Node " + node.key() + "）的子插槽 "
                                + child.getKey() + " 引用了不存在的 Node " + child.getValue().id());
                    }
                }
            }
        }

        // 3：环检测（DFS 三色）
        detectCycles(nodes, outErrors);

        // 4：契约需求（推论2）：目标插槽存在；目标插槽至少有一个分支满足契约（防死需求）
        for (Node<?> node : nodes.values()) {
            for (Branch<?> branch : node.branches().values()) {
                for (Requirement req : branch.requires()) {
                    Node<?> target = nodes.get(req.targetNodeId());
                    if (target == null) {
                        outErrors.add("Branch " + branch.id() + "（Node " + node.key() + "）的契约 "
                                + req + " 目标插槽不存在");
                        continue;
                    }
                    boolean anySatisfies = target.branches().values().stream()
                            .anyMatch(b -> req.isSatisfiedBy(b.factory().producedType()));
                    if (!anySatisfies) {
                        outErrors.add("Branch " + branch.id() + "（Node " + node.key() + "）的契约 "
                                + req + " 是死需求：目标插槽无任何分支满足");
                    }
                }
            }
        }

        // 5：全默认路径必须可满足（旧存档/未设置的回退组合必须合法）。
        // 仅在无环时执行（有环时默认路径走查可能不完整，环已由前面报错）。多根：每个根域各自校验。
        boolean acyclic = outErrors.stream().noneMatch(e -> e.contains("环"));
        if (acyclic) {
            for (ResourceLocation rootId : rootNodeIds) {
                Node<?> rootNode = nodes.get(rootId);
                if (rootNode != null) {
                    String violation = ConstraintSolver.checkDefaultsSatisfiable(nodes::get, rootNode.key());
                    if (violation != null) {
                        outErrors.add("根域 " + rootId + " 的全默认路径不满足契约: " + violation);
                    }
                }
            }
        }

        // 6：根可达性（多根并集）
        checkReachability(nodes, rootNodeIds, outErrors);
    }

    // ==================== 环检测（三色 DFS） ====================

    private static final int WHITE = 0; // 未访问
    private static final int GRAY = 1;  // 在栈上
    private static final int BLACK = 2; // 已完成

    private static void detectCycles(Map<ResourceLocation, Node<?>> nodes, List<String> outErrors) {
        Map<ResourceLocation, Integer> color = new HashMap<>();
        nodes.keySet().forEach(id -> color.put(id, WHITE));
        Deque<ResourceLocation> path = new ArrayDeque<>();
        for (ResourceLocation id : nodes.keySet()) {
            if (color.get(id) == WHITE) {
                dfs(id, nodes, color, path, outErrors);
            }
        }
    }

    private static void dfs(ResourceLocation nodeId, Map<ResourceLocation, Node<?>> nodes,
                            Map<ResourceLocation, Integer> color, Deque<ResourceLocation> path,
                            List<String> outErrors) {
        color.put(nodeId, GRAY);
        path.addLast(nodeId);
        Node<?> node = nodes.get(nodeId);
        if (node != null) {
            for (Branch<?> branch : node.branches().values()) {
                for (NodeKey<?> childKey : branch.children().values()) {
                    ResourceLocation childId = childKey.id();
                    Integer c = color.get(childId);
                    if (c == null) {
                        continue; // 引用不存在的 Node 已在前面报错
                    }
                    if (c == GRAY) {
                        outErrors.add("概念树存在环: " + formatCycle(path, childId));
                    } else if (c == WHITE) {
                        dfs(childId, nodes, color, path, outErrors);
                    }
                }
            }
        }
        path.removeLast();
        color.put(nodeId, BLACK);
    }

    private static String formatCycle(Deque<ResourceLocation> path, ResourceLocation backTo) {
        StringBuilder sb = new StringBuilder();
        boolean inCycle = false;
        for (ResourceLocation id : path) {
            if (id.equals(backTo)) {
                inCycle = true;
            }
            if (inCycle) {
                sb.append(id).append(" -> ");
            }
        }
        sb.append(backTo);
        return sb.toString();
    }

    // ==================== 根可达性 ====================

    private static void checkReachability(Map<ResourceLocation, Node<?>> nodes, Set<ResourceLocation> rootNodeIds,
                                          List<String> outErrors) {
        Set<ResourceLocation> reachable = new HashSet<>();
        Deque<ResourceLocation> stack = new ArrayDeque<>();
        for (ResourceLocation rootId : rootNodeIds) {
            if (!nodes.containsKey(rootId)) {
                outErrors.add("根 Node " + rootId + " 未注册");
                continue;
            }
            if (reachable.add(rootId)) {
                stack.push(rootId);
            }
        }
        while (!stack.isEmpty()) {
            Node<?> node = nodes.get(stack.pop());
            if (node == null) {
                continue;
            }
            for (Branch<?> branch : node.branches().values()) {
                for (NodeKey<?> childKey : branch.children().values()) {
                    if (reachable.add(childKey.id())) {
                        stack.push(childKey.id());
                    }
                }
            }
        }
        for (ResourceLocation id : nodes.keySet()) {
            if (!reachable.contains(id)) {
                outErrors.add("Node " + id + " 从根集 " + rootNodeIds + " 不可达（挂了但永远用不到）");
            }
        }
    }
}
