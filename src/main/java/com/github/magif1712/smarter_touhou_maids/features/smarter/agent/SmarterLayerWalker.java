package com.github.magif1712.smarter_touhou_maids.features.smarter.agent;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.PathTokenLogic;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.PersistableProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Branch;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.ConceptTree;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Node;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.NodeKey;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.RegistrySnapshot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Selection;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 遍历 smarter 模式各插槽当前选中分支的 factory（真善美第2条：意识域 C 中"遍历各层选中模式 factory"
 * 是<b>一个</b>模式，代码域 D 中也只应有<b>一个</b>实现，故提取为共享遍历器）。
 * <p>
 * <b>概念树驱动</b>：从根插槽（AGENT）出发，沿当前选中 Branch 声明的 children 递归——
 * sensor/effector/ai/process/mapper/nn 同构（都是可切换、可持久化、可展示的插槽），
 * 层次数量不限，visited 用<b>路径键</b>防重复（同一插槽在不同父下可出现多次）。
 * <p>
 * <b>用实在的东西转化不实在的概念</b>（真善美第4条）：把"遍历各层 factory"这个不实在的算法流程，
 * 实在化为一个类 + 一个回调契约。调用方只需关心"拿到 factory 后做什么"（渲染 EditBox / CycleButton），
 * 不关心遍历细节。两个 Panel（RuntimeParamsPanel / AgentDebugPanel）共用同一遍历器，零重复。
 * <p>
 * <b>Factory 级别</b>：遍历的是 factory（注册时就存在，不依赖 agent 实例），故附身前即可调用。
 * <p>
 * <b>随各层模式动态切换</b>：每次调用都经 {@link SelectionLoader#effectiveSelection} 取当前
 * 有效 Selection，ModeSelectorPanel 切换后 rebuildWidgets → 重新 walk → 各 Panel 拿到新
 * factory 列表 → 动态刷新。
 */
public final class SmarterLayerWalker {
    private SmarterLayerWalker() {
    }

    /**
     * 遍历 maid 当前选中的所有插槽分支 factory，对每个 factory 调 onFactory 回调。
     *
     * @param maid      女仆实体（per-maid 模式选择）
     * @param onFactory 回调：(nodeId, factory) — nodeId 标识当前插槽，factory 是该插槽选中分支的工厂
     */
    public static void walk(EntityMaid maid, BiConsumer<ResourceLocation, Object> onFactory) {
        RegistrySnapshot snapshot = ConceptTree.snapshot();
        if (snapshot == null) {
            return;
        }
        Selection selection = SelectionLoader.effectiveSelection(snapshot, maid);
        walkNode(snapshot, AgentNodeKeys.AGENT, selection, onFactory, new HashSet<>(), "");
    }

    private static void walkNode(RegistrySnapshot snapshot, NodeKey<?> nodeKey, Selection selection,
                                 BiConsumer<ResourceLocation, Object> onFactory,
                                 Set<String> visitedPaths, String path) {
        String pathKey = path + "/" + nodeKey.id();
        if (!visitedPaths.add(pathKey)) {
            return; // 路径防重复（同一插槽在不同父下可出现多次）
        }
        Node<?> node = snapshot.node(nodeKey);
        if (node == null) {
            return; // 附属未注册此插槽，跳过
        }
        Branch<?> branch = effectiveBranch(node, selection);
        if (branch == null) {
            return;
        }
        onFactory.accept(nodeKey.id(), branch.factory());
        for (Map.Entry<String, NodeKey<?>> child : branch.children().entrySet()) {
            walkNode(snapshot, child.getValue(), selection, onFactory, visitedPaths, pathKey);
        }
    }

    /** 当前生效分支：Selection 选中且存在 → 用之；否则回退默认分支（回退在此收敛）。
     *  GUI（ModeSelectorPanel）与持久化 token 共用同一生效语义（真善美第2条：单一读法）。 */
    @Nullable
    public static Branch<?> effectiveBranch(Node<?> node, Selection selection) {
        ResourceLocation selected = selection.branchOf(node.key());
        Branch<?> branch = selected != null ? node.branch(selected) : null;
        if (branch == null) {
            ResourceLocation def = node.defaultBranch();
            branch = def != null ? node.branch(def) : null;
        }
        return branch;
    }

    // ==================== 持久化支持 ====================

    /**
     * 推导 maid 当前完整根叶路径 token（如 "smarter__process_ai__urana__original_mapper__cnn"）。
     * <p>
     * 沿主轴链（agent → ai → process → mapper/nn，即非 sensor/effector 的 child 边）逐层下钻，
     * 收集每层选中分支的 id path 分量，用 {@code __} 拼接。sensor/effector 不纳入
     * （持久化数据按 AI 实现路径键控，与感受器/效应器无关）。
     * <p>
     * 拼接语义由 {@link PathTokenLogic} 纯函数承载，golden 样本测试锁死——
     * 此处只做概念树到纯数据层视图的适配。
     *
     * @param maid 女仆实体（per-maid 模式选择）
     * @return 路径 token（无选中时返回空串）
     */
    public static String pathToken(EntityMaid maid) {
        RegistrySnapshot snapshot = ConceptTree.snapshot();
        if (snapshot == null) {
            return "";
        }
        Selection selection = SelectionLoader.effectiveSelection(snapshot, maid);
        return PathTokenLogic.pathToken(
                AgentNodeKeys.AGENT.id().toString(),
                layerId -> layerViewOf(snapshot, layerId),
                layerId -> {
                    ResourceLocation selected = selection.branchOf(ResourceLocation.tryParse(layerId));
                    return selected != null ? selected.toString() : null;
                });
    }

    /**
     * 概念树 Node → {@link PathTokenLogic.Layer} 适配器。
     * 主轴 child = 非 sensor/effector 的 child 边（当前图至多一条；更多时不纳入 token）。
     */
    @Nullable
    private static PathTokenLogic.Layer layerViewOf(RegistrySnapshot snapshot, String layerId) {
        ResourceLocation nodeId = ResourceLocation.tryParse(layerId);
        Node<?> node = nodeId != null ? snapshot.node(nodeId) : null;
        if (node == null) {
            return null;
        }
        return new PathTokenLogic.Layer() {
            @Override
            public String defaultEntryId() {
                ResourceLocation def = node.defaultBranch();
                return def != null ? def.toString() : null;
            }

            @Override
            public String entryPath(String entryId) {
                ResourceLocation branchId = ResourceLocation.tryParse(entryId);
                Branch<?> branch = branchId != null ? node.branch(branchId) : null;
                return branch != null ? branch.id().getPath() : null;
            }

            @Override
            public String subLayerId(String entryId) {
                ResourceLocation branchId = ResourceLocation.tryParse(entryId);
                Branch<?> branch = branchId != null ? node.branch(branchId) : null;
                if (branch == null) {
                    return null;
                }
                for (Map.Entry<String, NodeKey<?>> child : branch.children().entrySet()) {
                    String name = child.getKey();
                    if (!"sensor".equals(name) && !"effector".equals(name)) {
                        return child.getValue().id().toString();
                    }
                }
                return null;
            }
        };
    }

    /**
     * 路径默认持久化开关 = 路径上任一 factory 实现 {@link PersistableProvider} 且 {@code hasPersistableData()=true}。
     * <p>
     * 与 ParamPanelProvider/DebugPanelProvider 的遍历检查同构（第三 Provider 管道）。
     * 遍历当前选中分支树的所有 factory，任一声明 true 即路径默认开。
     * <p>
     * <b>声明轨</b>（真善美第2条）：本方法返回路径的默认持久化开关，供 GUI 初值与 shutdown 判断。
     * 用户可在 GUI 覆盖（存 ParamStore，per-maid）。
     *
     * @param maid 女仆实体
     * @return true = 路径上任一 factory 声明有可持久化数据
     */
    public static boolean anyPersistable(EntityMaid maid) {
        boolean[] result = {false};
        walk(maid, (nodeId, factory) -> {
            if (factory instanceof PersistableProvider p && p.hasPersistableData()) {
                result[0] = true;
            }
        });
        return result[0];
    }
}
