package com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.panels;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterLayerWalker;
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
 * <b>纯 text 透传</b>（真善美第4条）：本 Panel 不感知值类型——不 parse、不 clamp。
 * 提交时调 {@link ParamOption#commitText} 传入 text，回显时调 {@link ParamOption#currentText}
 * 取管道中的 String。解读 + 约束由 factory 在 textProcessor 中负责。
 * <p>
 * <b>按 controlHint 分派控件</b>（真善美第2条）：控件选择是 config_gui 的内部事务。
 * factory 经 {@link ParamOption#controlHint} 声明建议控件，本 Panel 据此分派：
 * "toggle" → CycleButton 即点即提交，其余 → EditBox 兜底（含 "text" 与未知 hint）。
 * 附属 config_gui 可定义不同 hint 集合，管道与 factory 零改动。
 * hint 是参数项的静态展示属性（与 label/tooltip 同性质），不进 ParamStore、不进网络包。
 * <p>
 * <b>随各层模式动态切换</b>：选 process=urana → UranaProcessFactory 暴露快/慢环参数；
 * 选 agent=reflex_arc → ReflexArcSystemAgentFactory 暴露允许附身开关。
 * 不实现 ParamPanelProvider 的 factory 自动跳过，GUI 零改动。
 * <p>
 * <b>遍历逻辑共享</b>：与 AgentDebugPanel 共用 {@link SmarterLayerWalker}，
 * 本类只关心"拿到 factory 后渲染控件"，不重复遍历算法（真善美第2条：C 中一个模式 D 中也一个）。
 * <p>
 * <b>commit 时机</b>：EditBox 靠 {@link #commitPending()} 在 removed/rebuildWidgets/ENTER 时提交。
 * Screen 统一在 rebuildWidgets 前调各 Panel commitPending，防丢 EditBox 输入。
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
     * 从 factory 提取参数项并渲染。全部用 EditBox（普适兜底）。
     * factory 不实现 ParamPanelProvider 时跳过（该层无可调参数）。
     */
    private void addParamsFromFactory(EntityMaid maid, PanelContext ctx, VerticalStack stack,
                                      Object factory) {
        if (!(factory instanceof ParamPanelProvider)) {
            return;
        }
        List<ParamOption> params = ((ParamPanelProvider) factory).getParamOptions();
        for (ParamOption opt : params) {
            addParam(maid, ctx, stack, opt);
        }
    }

    /**
     * 按 controlHint 分派控件（真善美第2条：控件选择是 config_gui 内部事务）。
     * standard_config_gui 认识的 hint 在此 case，未知 hint 降级 EditBox 兜底。
     * 附属 config_gui 可定义不同 hint 集合，管道与 factory 零改动。
     */
    private void addParam(EntityMaid maid, PanelContext ctx, VerticalStack stack, ParamOption opt) {
        switch (opt.controlHint()) {
            case "toggle" -> addToggleParam(maid, stack, opt);
            default -> addTextParam(maid, ctx, stack, opt);  // 兜底含 "text" 与未知
        }
    }

    /** boolean 开关 → CycleButton on/off，即点即提交（不进 pending，与 EditBox 延迟提交并存）。 */
    private void addToggleParam(EntityMaid maid, VerticalStack stack, ParamOption opt) {
        ConfigRow row = stack.addRow();
        boolean current = Boolean.parseBoolean(opt.currentText(maid));
        CycleButton<Boolean> btn = CycleButton.onOffBuilder(current)
                .create(row.x(), row.y(), 200, 20, opt.label(),
                        (b, v) -> opt.commitText(maid, String.valueOf(v)));
        btn.setTooltip(Tooltip.create(opt.tooltip()));
        row.addWidget(btn);
    }

    /** 文本输入参数 → EditBox，commitPending 时经 commitText 提交（不 parse 不 clamp）。 */
    private void addTextParam(EntityMaid maid, PanelContext ctx, VerticalStack stack, ParamOption opt) {
        ConfigRow row = stack.addRow();
        EditBox box = new EditBox(ctx.font, row.x(), row.y(), 200, 20, opt.label());
        box.setValue(opt.currentText(maid));
        box.setTooltip(Tooltip.create(opt.tooltip()));
        row.addWidget(box);
        pendingBoxes.add(new ParamBox(maid, opt, box));
    }

    @Override
    public void commitPending() {
        for (ParamBox pb : pendingBoxes) {
            String trimmed = pb.box.getValue().trim();
            if (!trimmed.isEmpty()) {
                pb.option.commitText(pb.maid, trimmed);
            }
        }
    }

    /** 缓存 EditBox + maid + ParamOption，供 commitPending 提交。 */
    private static final class ParamBox {
        final EntityMaid maid;
        final ParamOption option;
        final EditBox box;

        ParamBox(EntityMaid maid, ParamOption option, EditBox box) {
            this.maid = maid;
            this.option = option;
            this.box = box;
        }
    }
}
