package com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.panels;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterLayerWalker;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.BoolParamOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.LongParamOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamPanelProvider;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.IConfigPanel;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.PanelContext;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.layout.ConfigRow;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.layout.VerticalStack;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行参数面板：遍历 smarter 模式各层选中的 entry，从每层 factory 的 {@link ParamPanelProvider}
 * 汇总参数项渲染（per-maid）。
 * <p>
 * <b>值类型多态渲染</b>（真善美第2条）：ParamOption 是 sealed 契约，本 Panel 按值类型分支：
 * <ul>
 *   <li>{@link LongParamOption} → EditBox，commitPending 时 parse long（数值参数如快/慢环 minDt）。</li>
 *   <li>{@link BoolParamOption} → CycleButton.onOff，即时 commit（布尔开关如允许附身）。</li>
 * </ul>
 * 新增值类型只需新增一个渲染分支（sealed 穷尽性保证不遗漏）。
 * <p>
 * <b>随各层模式动态切换</b>：选 process=urana → UranaProcessFactory 暴露快/慢环参数；
 * 选 agent=reflex_arc → ReflexArcSystemAgentFactory 暴露允许附身开关。
 * 不实现 ParamPanelProvider 的 factory 自动跳过，GUI 零改动。
 * <p>
 * <b>遍历逻辑共享</b>：与 AgentDebugPanel 共用 {@link SmarterLayerWalker}，
 * 本类只关心"拿到 factory 后渲染控件"，不重复遍历算法（真善美第2条：C 中一个模式 D 中也一个）。
 * <p>
 * <b>commit 时机</b>：EditBox 靠 {@link #commitPending()} 在 removed/rebuildWidgets/ENTER 时提交。
 * CycleButton 即时 commit（onChange 调 setter），不经 pendingBoxes。Screen 统一在 rebuildWidgets
 * 前调各 Panel commitPending，防丢 EditBox 输入。
 */
@OnlyIn(Dist.CLIENT)
public class RuntimeParamsPanel implements IConfigPanel {
    private final List<ParamBox> pendingBoxes = new ArrayList<>();

    @Override
    public Component getTitle() {
        return Component.translatable("panel.smarter_touhou_maids.runtime_params");
    }

    @Override
    public void buildWidgets(PanelContext ctx, VerticalStack stack) {
        pendingBoxes.clear();
        EntityMaid maid = ctx.maid;
        if (maid == null) {
            return;
        }
        // 共享遍历器：对每层选中 factory 调回调，factory 不实现 ParamPanelProvider 时跳过
        SmarterLayerWalker.walk(maid, (registryId, factory) ->
                addParamsFromFactory(maid, ctx, stack, factory));
    }

    /**
     * 从 factory 提取参数项并按值类型渲染。
     * factory 不实现 ParamPanelProvider 时跳过（该层无可调参数）。
     */
    private void addParamsFromFactory(EntityMaid maid, PanelContext ctx, VerticalStack stack,
                                      Object factory) {
        if (!(factory instanceof ParamPanelProvider)) {
            return;
        }
        List<ParamOption> params = ((ParamPanelProvider) factory).getParamOptions();
        for (ParamOption opt : params) {
            // sealed 穷尽：LongParamOption / BoolParamOption（新增值类型时编译器强制补分支）
            if (opt instanceof LongParamOption lpo) {
                addLongParam(maid, ctx, stack, lpo);
            } else if (opt instanceof BoolParamOption bpo) {
                addBoolParam(maid, stack, bpo);
            }
        }
    }

    /** 数值参数 → EditBox，commitPending 时 parse long。 */
    private void addLongParam(EntityMaid maid, PanelContext ctx, VerticalStack stack, LongParamOption opt) {
        ConfigRow row = stack.addRow();
        EditBox box = new EditBox(ctx.font, row.x(), row.y(), 200, 20, opt.label());
        box.setValue(String.valueOf(opt.get(maid)));
        box.setTooltip(Tooltip.create(opt.tooltip()));
        row.addWidget(box);
        pendingBoxes.add(new ParamBox(maid, opt, box));
    }

    /** 布尔开关 → CycleButton.onOff，onChange 即时 commit（不经 pending）。 */
    private void addBoolParam(EntityMaid maid, VerticalStack stack, BoolParamOption opt) {
        ConfigRow row = stack.addRow();
        CycleButton<Boolean> btn = CycleButton.onOffBuilder(opt.get(maid))
                .create(row.x(), row.y(), 200, 20, opt.label(),
                        (b, value) -> opt.set(maid, value));
        if (opt.tooltip() != null) {
            btn.setTooltip(Tooltip.create(opt.tooltip()));
        }
        row.addWidget(btn);
    }

    @Override
    public void commitPending() {
        for (ParamBox pb : pendingBoxes) {
            String trimmed = pb.box.getValue().trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                long parsed = Long.parseLong(trimmed);
                long value = Math.max(0, Math.min(5000, parsed));
                String normalized = String.valueOf(value);
                if (!normalized.equals(pb.box.getValue())) {
                    pb.box.setValue(normalized);
                }
                pb.option.set(pb.maid, value);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /** 缓存 EditBox + maid + LongParamOption，供 commitPending 提交。 */
    private static final class ParamBox {
        final EntityMaid maid;
        final LongParamOption option;
        final EditBox box;

        ParamBox(EntityMaid maid, LongParamOption option, EditBox box) {
            this.maid = maid;
            this.option = option;
            this.box = box;
        }
    }
}
