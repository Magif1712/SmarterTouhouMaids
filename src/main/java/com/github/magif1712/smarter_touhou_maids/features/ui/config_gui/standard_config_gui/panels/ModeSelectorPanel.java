package com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.panels;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.AgentNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SelectionLoader;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterClientState;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterLayerWalker;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Branch;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.ConceptTree;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.ConstraintSolver;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Node;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.NodeKey;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.RegistrySnapshot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Selection;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.IConfigPanel;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.PanelContext;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.layout.ConfigRow;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.layout.VerticalStack;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Smarter 各插槽的模式选择面板：概念树递归展开（per-maid）。
 * <p>
 * <b>概念树驱动</b>：从根插槽（AGENT）出发，沿当前选中 Branch 声明的 children 递归展开——
 * sensor/effector/ai/process/mapper/nn 同构（都是可切换、可展示的插槽），层次数量不限，
 * visited 用<b>路径键</b>防重复（同一插槽在不同父下可出现多次，如两个 mapper 各自的 NN 插槽）。
 * <p>
 * <b>推论2 落地</b>：选项经 {@link ConstraintSolver#selectableBranches} 过滤——只列
 * "代入后整体仍可满足"的分支（Ext(D) 内元素），玩家无法选到不兼容组合（如旧流程+新感受器）。
 * <p>
 * <b>级联修正</b>：选定分支后用 {@link ConstraintSolver#solveAssignment} 求见证赋值，
 * 把与之冲突的其它已存选择重置为见证值（如选 urana_original 时自动把 sensor 切到推模型）。
 * <p>
 * 改选择时 callback 调 rebuildTrigger 触发 Screen 重建下层按钮。
 */
@OnlyIn(Dist.CLIENT)
public class ModeSelectorPanel implements IConfigPanel {
    @Override
    public Component getTitle() {
        return Component.translatable("panel.smarter_touhou_maids.mode_selector");
    }

    @Override
    public void buildWidgets(PanelContext ctx, VerticalStack stack) {
        EntityMaid maid = ctx.maid;
        if (maid == null) {
            return;
        }
        RegistrySnapshot snapshot = ConceptTree.snapshot();
        if (snapshot == null) {
            return;
        }
        Selection selection = SelectionLoader.effectiveSelection(snapshot, maid);
        buildNodeSelectors(maid, ctx, stack, snapshot, selection, AgentNodeKeys.AGENT, new HashSet<>(), "");
    }

    /**
     * 递归展开一个插槽的选择按钮，并沿当前生效分支的 children 下钻。
     * 选项过滤：只列 ConstraintSolver 判定可选的分支（推论2）。
     */
    private void buildNodeSelectors(EntityMaid maid, PanelContext ctx, VerticalStack stack,
                                    RegistrySnapshot snapshot, Selection selection, NodeKey<?> nodeKey,
                                    Set<String> visitedPaths, String path) {
        String pathKey = path + "/" + nodeKey.id();
        if (!visitedPaths.add(pathKey)) {
            return;
        }
        Node<?> node = snapshot.node(nodeKey);
        if (node == null) {
            return; // 附属未注册此插槽，跳过
        }

        // 当前生效分支（Selection 选中且存在 → 用之；否则默认）
        Branch<?> currentBranch = SmarterLayerWalker.effectiveBranch(node, selection);
        ResourceLocation currentId = currentBranch != null ? currentBranch.id() : null;

        // 推论2：可选集 = 代入后整体仍可满足的分支
        Set<ResourceLocation> selectable = new LinkedHashSet<>();
        ConstraintSolver.selectableBranches(snapshot.nodes()::get, AgentNodeKeys.AGENT, nodeKey,
                selection, /*->*/ selectable);
        // 当前生效分支始终可见（即使因残留选择暂时不可满足——展示真实现状，级联修正会拉回合法）
        List<ResourceLocation> values = node.branches().keySet().stream()
                .filter(id -> selectable.contains(id) || id.equals(currentId))
                .toList();
        if (values.isEmpty() || currentId == null) {
            return;
        }

        ConfigRow row = stack.addRow();
        CycleButton<ResourceLocation> btn = CycleButton.<ResourceLocation>builder(valueToText(node))
                .withValues(values)
                .withInitialValue(currentId)
                .create(row.x(), row.y(), 200, 20,
                        nodeTitle(nodeKey),
                        (b, selectedId) -> {
                            applySelectionWithCascade(maid, snapshot, selection, nodeKey, selectedId);
                            ctx.rebuildTrigger.run();
                        });
        row.addWidget(btn);

        // 递归：沿当前生效分支的 children 下钻
        if (currentBranch != null) {
            for (Map.Entry<String, NodeKey<?>> child : currentBranch.children().entrySet()) {
                buildNodeSelectors(maid, ctx, stack, snapshot, selection, child.getValue(), visitedPaths, pathKey);
            }
        }
    }

    /**
     * 选定分支 + 级联修正：以刚选的值为锚，其余已存条目贪心最大保留（能满足才保留、
     * 冲突才丢弃），求解见证后：丢弃的条目清除（回退默认），见证中不同的条目重置为见证值，
     * 最后写入选定本身。
     * <p>
     * 例：当前 {process=urana_original, sensor=push}，用户把 process 切回 urana（新）——
     * push 与 urana 冲突 → push 被丢弃清除（sensor 回退默认拉模型），组合合法。
     * 反向：当前 {process=urana, sensor=pull}，用户选 sensor=push → process 与 push 冲突
     * → 见证把 process 重置为 urana_original。两个方向都不死锁（bug 修复）。
     */
    private static void applySelectionWithCascade(EntityMaid maid, RegistrySnapshot snapshot, Selection selection,
                                                  NodeKey<?> nodeKey, ResourceLocation selectedId) {
        Selection fixed = selection.with(nodeKey, selectedId);
        Map<ResourceLocation, ResourceLocation> witness = new LinkedHashMap<>();
        if (!ConstraintSolver.solveAssignment(snapshot.nodes()::get, AgentNodeKeys.AGENT, fixed, /*->*/ witness)) {
            // 全固定不可满足 → 贪心最大保留：以刚选值为锚，其余已存条目逐个试加
            Map<ResourceLocation, ResourceLocation> kept = new LinkedHashMap<>();
            kept.put(nodeKey.id(), selectedId);
            List<ResourceLocation> dropped = new java.util.ArrayList<>();
            for (Map.Entry<ResourceLocation, ResourceLocation> e : selection.choices().entrySet()) {
                if (e.getKey().equals(nodeKey.id())) {
                    continue;
                }
                Selection trial = new Selection(kept).with(new NodeKey<>(e.getKey(), Object.class), e.getValue());
                if (ConstraintSolver.isSatisfiable(snapshot.nodes()::get, AgentNodeKeys.AGENT, trial)) {
                    kept.put(e.getKey(), e.getValue());
                } else {
                    dropped.add(e.getKey());
                }
            }
            // 丢弃的条目清除（回退默认分支）
            for (ResourceLocation droppedId : dropped) {
                SmarterClientState.INSTANCE.clearMode(maid, droppedId);
            }
            witness.clear();
            ConstraintSolver.solveAssignment(snapshot.nodes()::get, AgentNodeKeys.AGENT,
                    new Selection(kept), /*->*/ witness);
        }
        // 见证修正：活动树内与当前生效选择不同的插槽，重置为见证值（不覆盖刚选的插槽）
        witness.forEach((nodeId, branchId) -> {
            if (!nodeId.equals(nodeKey.id()) && !branchId.equals(selection.branchOf(nodeId))) {
                SmarterClientState.INSTANCE.setMode(maid, nodeId, branchId);
            }
        });
        SmarterClientState.INSTANCE.setMode(maid, nodeKey.id(), selectedId);
    }

    private static Function<ResourceLocation, Component> valueToText(Node<?> node) {
        return id -> {
            Branch<?> branch = node.branch(id);
            return branch != null
                    ? Component.translatable(branch.meta().displayNameKey())
                    : Component.literal(id.toString());
        };
    }

    /** 插槽标题（如 agent/ai/process/nn/sensor/effector）的 i18n key。 */
    private static Component nodeTitle(NodeKey<?> nodeKey) {
        return Component.translatable("mode.smarter_touhou_maids.registry." + nodeKey.id().getPath());
    }
}
