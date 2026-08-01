package com.github.magif1712.smarter_touhou_maids.features.ui.standard.panels;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterLayerWalker;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug.DebugOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug.DebugPanelProvider;
import com.github.magif1712.smarter_touhou_maids.features.ui.standard.IConfigPanel;
import com.github.magif1712.smarter_touhou_maids.features.ui.standard.PanelContext;
import com.github.magif1712.smarter_touhou_maids.features.ui.standard.layout.ConfigRow;
import com.github.magif1712.smarter_touhou_maids.features.ui.standard.layout.VerticalStack;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 通用调试面板：遍历 smarter 模式各层选中的 entry，从每层 factory 的 {@link DebugPanelProvider}
 * 汇总调试项渲染 CycleButton。
 * <p>
 * <b>Factory 级别而非 agent 实例级别</b>：调试项由各层 Factory 实现 DebugPanelProvider 暴露
 * （Factory 在注册时就存在，不需要附身创建 agent 实例）。故附身前即可查看和切换调试开关。
 * <p>
 * <b>随各层模式动态切换</b>（真善美第2条）：遍历 agent→ai→process→nn 递归链 + sensor/effector 叶子，
 * 每层选中的 entry 的 factory 若实现 DebugPanelProvider 即渲染其调试项。
 * 用户在 ModeSelectorPanel 切换某层模式后 rebuildWidgets → 本 Panel 重新遍历 → 调试项动态切换。
 * <p>
 * <b>遍历逻辑共享</b>：与 RuntimeParamsPanel 共用 {@link SmarterLayerWalker}，
 * 本类只关心"拿到 factory 后渲染 CycleButton"，不重复遍历算法（真善美第2条：C 中一个模式 D 中也一个）。
 * <p>
 * <b>数据驱动</b>（消除硬编码）：本 Panel 不感知调试项背后的具体实现
 * （VisionDebugHook / static dt / EffectorDebugHook），只消费 DebugOption 列表。
 * 换某层 factory 时——
 * <ul>
 *   <li>新 factory 实现 DebugPanelProvider：本 Panel 自动显示其调试项，GUI 零改动。</li>
 *   <li>新 factory 不实现：本 Panel 不显示该层调试项，GUI 零改动。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class AgentDebugPanel implements IConfigPanel {
    @Override
    public Component getTitle() {
        return Component.translatable("panel.smarter_touhou_maids.debug");
    }

    @Override
    public void buildWidgets(PanelContext ctx, VerticalStack stack) {
        EntityMaid maid = ctx.maid;
        if (maid == null) {
            return;
        }
        // 共享遍历器：对每层选中 factory 调回调，factory 不实现 DebugPanelProvider 时跳过
        SmarterLayerWalker.walk(maid, (registryId, factory) ->
                addDebugOptionsFromFactory(factory, stack));
    }

    /**
     * 从 factory 提取调试项并渲染 CycleButton。
     * factory 不实现 DebugPanelProvider 时跳过（该层无调试项）。
     */
    private void addDebugOptionsFromFactory(Object factory, VerticalStack stack) {
        if (!(factory instanceof DebugPanelProvider)) {
            return;
        }
        List<DebugOption> options = ((DebugPanelProvider) factory).getDebugOptions();
        for (DebugOption opt : options) {
            ConfigRow row = stack.addRow();
            CycleButton<Boolean> btn = CycleButton.onOffBuilder(opt.get())
                    .create(row.x(), row.y(), 200, 20,
                            opt.label(),
                            (b, v) -> opt.set(v));
            if (opt.tooltip() != null) {
                btn.setTooltip(Tooltip.create(opt.tooltip()));
            }
            row.addWidget(btn);
        }
    }
}
