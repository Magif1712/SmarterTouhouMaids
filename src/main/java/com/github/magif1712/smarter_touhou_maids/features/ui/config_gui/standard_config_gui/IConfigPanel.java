package com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui;

import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.layout.VerticalStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 一个可对接配置面板的顶层契约（类比 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.IAgent}）。
 * <p>
 * 一个 Panel = GUI 中的一个配置区（附身配置 / AI 模式选择 / 运行参数 / Agent 调试 / 附属自定义）。
 * Screen 是 Panel 容器，遍历 {@link PanelRegistry#all()} 按 {@link #getTitle()} 分区、
 * 调 {@link #buildWidgets} 堆叠渲染。
 * <p>
 * <b>模式层级</b>（真善美第2条）：Screen 是上层（Panel 容器），Panel 是下层（Screen 的模式）。
 * Screen 换一种 Panel 集合（附属增减 Panel）时，Screen 代码零改动即正确运行。
 * <p>
 * <b>无 Factory</b>（"真"）：Agent 需要 Factory 是因为有下层注入组装；Panel 是无状态视图
 * （每次 init 重新 buildWidgets，不持跨 rebuild 状态），附属直接注册实例即可，
 * 不需要 PanelFactory 这个 C 中不存在的抽象。
 * <p>
 * <b>buildWidgets 幂等</b>：rebuildWidgets 触发 init() 重跑时本方法重跑，
 * 结果应一致——不需要单独的 rebuild 钩子（"真"：不引入 C 中没有的抽象）。
 */
@OnlyIn(Dist.CLIENT)
public interface IConfigPanel {
    /** 该 Panel 的分区标题（i18n），Screen 据此画分区标题。 */
    Component getTitle();

    /**
     * 构建 Panel 的 widgets，向 stack 追加行（ConfigRow）。
     * 在 Screen.init() 遍历 Panel 时调用。幂等。
     *
     * @param ctx   上下文（maid/agent/font/原点坐标），Panel 各取所需
     * @param stack 垂直堆叠容器，Panel 向其追加 ConfigRow
     */
    void buildWidgets(PanelContext ctx, VerticalStack stack);

    /**
     * 提交未保存的输入（如 EditBox 的值）。
     * Screen 在 {@code removed()} 与 {@code rebuildWidgets()} 前统一调用，避免丢输入。
     * 无输入态的 Panel 可用默认空实现。
     */
    default void commitPending() {
    }
}
