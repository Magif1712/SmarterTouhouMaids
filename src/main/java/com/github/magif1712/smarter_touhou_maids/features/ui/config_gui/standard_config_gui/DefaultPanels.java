package com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui;

import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.panels.AgentDebugPanel;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.panels.ModeSelectorPanel;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.panels.PersistenceConfigPanel;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.panels.RuntimeParamsPanel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 主模组默认 Panel 注册：把内置 4 个配置面板注册到 {@link PanelRegistry}。
 * <p>
 * 由 {@code ClientSetup} 在 {@code FMLClientSetupEvent} 调用。
 * 附属模组在自己的 setup event 追加注册自己的 Panel，主模组 Panel 先注册排在前。
 * <p>
 * 注册顺序即 GUI 显示顺序：
 * AI 模式选择 → 运行参数 → 持久化配置 → Agent 调试。
 * <p>
 * <b>持久化配置面板</b>：路径键控全局配置（开关 + save 间隔 + 最大保留版本数）。
 * 与 RuntimeParamsPanel 的 per-maid 参数不同——持久化配置是路径的属性（所有 maid 同路径共享），
 * 故独立成 Panel，不混入 RuntimeParamsPanel（真善美第2条：C 中两种配置维度 D 中也两种）。
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
        PanelRegistry.INSTANCE.register(new PersistenceConfigPanel());
        PanelRegistry.INSTANCE.register(new AgentDebugPanel());
    }
}
