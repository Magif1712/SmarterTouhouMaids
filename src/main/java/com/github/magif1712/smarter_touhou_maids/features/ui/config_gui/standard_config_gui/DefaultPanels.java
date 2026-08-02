package com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui;

import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.panels.AgentDebugPanel;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.panels.ModeSelectorPanel;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.panels.RuntimeParamsPanel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 主模组默认 Panel 注册：把内置 3 个配置面板注册到 {@link PanelRegistry}。
 * <p>
 * 由 {@code ClientSetup} 在 {@code FMLClientSetupEvent} 调用。
 * 附属模组在自己的 setup event 追加注册自己的 Panel，主模组 Panel 先注册排在前。
 * <p>
 * 注册顺序即 GUI 显示顺序：
 * AI 模式选择 → 运行参数 → Agent 调试。
 * <p>
 * <b>附身控制面板已移除</b>：原 PossessionPanel 含"允许附身"和"启用 Smarter"两开关。
 * "启用 Smarter"开关已去掉（smarter 启用=自动任务模式，不再有独立开关）；
 * "允许附身"经 ReflexArcSystemAgentFactory 实现 ParamPanelProvider 数据驱动暴露
 * （属 ReflexArcSystemAgent 特有配置，随 agent 出现/消失，与 minDt 同机制）。
 */
@OnlyIn(Dist.CLIENT)
public final class DefaultPanels {
    private DefaultPanels() {
    }

    public static void registerDefaults() {
        PanelRegistry.INSTANCE.register(new ModeSelectorPanel());
        PanelRegistry.INSTANCE.register(new RuntimeParamsPanel());
        PanelRegistry.INSTANCE.register(new AgentDebugPanel());
    }
}
