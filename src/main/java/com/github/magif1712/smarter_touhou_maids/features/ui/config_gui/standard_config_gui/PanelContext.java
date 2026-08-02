package com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.gui.Font;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * Panel 构建上下文：一次性注入 Panel 可能需要的所有外部依赖，Panel 各取所需
 * （真善美第1条"真"：每层只注入自己直接使用的那个抽象）。
 * <ul>
 *   <li>{@link com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.panels.ModeSelectorPanel} 用 {@link #maid} + {@link #rebuildTrigger}</li>
 *   <li>{@link com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.panels.AgentDebugPanel} 用 {@link #maid}（遍历各层 registry 取选中 entry）</li>
 *   <li>{@link com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.panels.RuntimeParamsPanel} 用 {@link #maid}（SmarterLayerWalker 遍历各层 factory 收集 ParamOption）</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class PanelContext {
    @Nullable
    public final EntityMaid maid;
    public final Font font;
    public final int leftPos;
    public final int topPos;
    /**
     * 触发 Screen 重建（rebuildWidgets）的回调。
     * Panel（如 ModeSelectorPanel）在用户改选某层后调本回调重建下层按钮。
     * Screen 在 override 的 rebuildWidgets 中先 commitPending 再 super，防丢输入。
     * 不需要重建的 Panel 忽略本字段。
     */
    public final Runnable rebuildTrigger;

    public PanelContext(@Nullable EntityMaid maid, Font font,
                        int leftPos, int topPos, Runnable rebuildTrigger) {
        this.maid = maid;
        this.font = font;
        this.leftPos = leftPos;
        this.topPos = topPos;
        this.rebuildTrigger = rebuildTrigger;
    }
}
