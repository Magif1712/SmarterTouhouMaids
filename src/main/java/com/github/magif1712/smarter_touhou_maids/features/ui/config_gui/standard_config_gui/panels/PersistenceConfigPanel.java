package com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.panels;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.PersistenceConfigProvider;
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
 * 持久化配置面板：per-maid 持久化配置（开关 + save 间隔 + 最大保留版本数）。
 * <p>
 * <b>统一参数管道</b>（真善美第2/4条）：本 Panel 不硬编码 label/读写逻辑——从
 * {@link PersistenceConfigProvider} 取 {@link ParamOption} 实例，用其 label/tooltip/
 * currentText/commitText 数据驱动渲染控件。配置存 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamStore}
 * （maid NBT，per-maid，随存档走）。
 * <p>
 * <b>两级开关分层</b>（C10 递归）：第一级"持久化开关"（总开关，控制终态 save）；
 * 第二级"定时持久化开关"（子开关，仅总开关开时呈现，控制运行中定期 save）；
 * 第三级"保存间隔旋钮"（仅子开关开时呈现）。maxRetention 旋钮归总开关管
 * （无论定时/终态 save 都产生版本，都需 prune）。每级开关切换后调
 * {@link PanelContext#rebuildTrigger} 重建 Panel，下层控件出现/消失。
 * <p>
 * <b>两级嵌套归呈现层</b>（真善美第3条）：两级嵌套是持久化配置特有的呈现模式，
 * 由本 Panel 自管（按固定结构 + 便捷读取方法判断可见性）。{@link ParamOption} 是数据声明，
 * 不携带可见性逻辑（符合"管道不解读"哲学）。4 项配置固定，结构固定。
 * <p>
 * <b>按 controlHint 分派控件</b>（真善美第2条）：控件选择是 config_gui 的内部事务。
 * {@code "toggle"} → CycleButton 即点即提交，其余 → EditBox 兜底（含 {@code "text"} 与未知 hint）。
 * standard_config_gui 坚持 EditBox 兜底——附属 config_gui 可认识 {@code "slider"} 等 hint，
 * 写自己的 Panel 遍历 {@link PersistenceConfigProvider#getOptions()} 用滑块渲染，注册到 PanelRegistry
 * （追加不覆盖），玩家经 GUI 选择器切换。
 * <p>
 * <b>commit 时机</b>：EditBox 靠 {@link #commitPending()} 在 removed/rebuildWidgets 时提交。
 * Toggle 即点即提交（CycleButton 回调直接 commitText + 触发 rebuild）。
 */
@OnlyIn(Dist.CLIENT)
public class PersistenceConfigPanel implements IConfigPanel {

    /** 缓存 EditBox 的提交上下文（maid + ParamOption + box），供 commitPending 统一执行。 */
    private final List<ParamBox> pendingBoxes = new ArrayList<>();

    @Override
    public Component getTitle() {
        return Component.translatable("panel.smarter_touhou_maids.persistence");
    }

    @Override
    public void buildWidgets(PanelContext ctx, VerticalStack stack) {
        pendingBoxes.clear();
        EntityMaid maid = ctx.maid;
        if (maid == null) {
            return;
        }

        // 便捷读取当前开关状态（判断两级嵌套可见性）
        boolean enabled = PersistenceConfigProvider.isPersistenceEnabled(maid);
        boolean periodicEnabled = PersistenceConfigProvider.isPeriodicSaveEnabled(maid);

        // === 第一级：总开关（是否启用持久化，控制终态 save）始终呈现 ===
        addToggle(maid, stack, ctx, PersistenceConfigProvider.persistenceEnabled());

        // === 第二级：仅总开关开时呈现 ===
        if (enabled) {
            // 子开关：定时持久化开关（与总开关正交——关闭不影响终态 save）
            addToggle(maid, stack, ctx, PersistenceConfigProvider.periodicSaveEnabled());

            // === 第三级：仅定时开关开时呈现保存间隔旋钮 ===
            if (periodicEnabled) {
                addText(maid, ctx, stack, PersistenceConfigProvider.saveIntervalSeconds());
            }

            // maxRetention 归总开关管（无论定时/终态 save 都产生版本，都需 prune）
            addText(maid, ctx, stack, PersistenceConfigProvider.maxRetention());
        }
    }

    /**
     * boolean 开关 → CycleButton on/off，即点即提交（不进 pending，与 EditBox 延迟提交并存）。
     * 切换后触发 rebuild（下层控件出现/消失）。
     */
    private void addToggle(EntityMaid maid, VerticalStack stack, PanelContext ctx, ParamOption opt) {
        ConfigRow row = stack.addRow();
        boolean current = Boolean.parseBoolean(opt.currentText(maid));
        CycleButton<Boolean> btn = CycleButton.onOffBuilder(current)
                .create(row.x(), row.y(), 200, 20, opt.label(),
                        (b, v) -> {
                            opt.commitText(maid, String.valueOf(v));
                            ctx.rebuildTrigger.run();
                        });
        btn.setTooltip(Tooltip.create(opt.tooltip()));
        row.addWidget(btn);
    }

    /**
     * 文本输入参数 → EditBox，commitPending 时经 commitText 提交。
     * standard_config_gui 坚持 EditBox 兜底（不认识 slider，附属可写自己的 Panel）。
     */
    private void addText(EntityMaid maid, PanelContext ctx, VerticalStack stack, ParamOption opt) {
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
