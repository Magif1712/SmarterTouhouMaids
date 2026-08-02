package com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui;

import com.github.magif1712.smarter_touhou_maids.features.maid.menu.AutoTaskConfigMenu;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.layout.ConfigRow;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.layout.VerticalStack;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * "自动任务"配置界面（薄容器）：遍历 {@link PanelRegistry} 中所有已注册 Panel，
 * 按顺序垂直堆叠渲染。
 * <p>
 * 本类只关心"按顺序堆叠所有 Panel + commit/重建时序 + 滚动/裁剪"，不感知任何具体配置项
 * （附身/模式选择/参数/调试都下沉到各 Panel）。附属增减 Panel 时本类零改动
 * （真善美第2条：Screen 换一种 Panel 集合实现不改代码也能正确运行）。
 * <p>
 * 调试开关不再硬编码：由 AgentDebugPanel 经
 * {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug.DebugPanelProvider}
 * 数据驱动渲染，换 Agent 时 GUI 零改动。
 * <p>
 * <b>布局实在化 + 滚动</b>：内容超出 viewport 时由 {@link VerticalStack} 管理 scrollOffset，
 * 本类用 scissor 裁剪 widget/label、画滚动条。把"内容溢出"这个不实在的约束，
 * 实在化为 viewport/content/scrollOffset 三量关系（真善美第3条）。
 * <p>
 * <b>分阶段渲染</b>（不调 super.render）：super 内部把 bg/widgets/labels/tooltip 绑在一起，
 * 无法选择性裁剪。本类自行分阶段：
 * <ol>
 *   <li>renderBg（背景，不裁）</li>
 *   <li>标题（不裁，固定在 viewport 上方）</li>
 *   <li>enableScissor → widgets + 内容 labels（裁）→ disableScissor</li>
 *   <li>滚动条（不裁）</li>
 *   <li>widget tooltip：由 Screen.renderWithTooltip 在 render() 返回后渲染，不在此处，故不被裁</li>
 * </ol>
 */
@OnlyIn(Dist.CLIENT)
public class AutoTaskConfigScreen extends AbstractContainerScreen<AutoTaskConfigMenu> {
    private static final int BG_COLOR = 0xCC000000;
    private static final int GUI_WIDTH = 380;
    private static final int GUI_HEIGHT_MAX = 410;
    private static final int CONTENT_X_OFFSET = 10;
    private static final int START_Y_OFFSET = 25;
    private static final int TITLE_COLOR = 0x39c5bb;
    private static final int PANEL_SPACER = 6;
    private static final int SCROLLBAR_X_OFFSET = 10;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int VIEWPORT_MARGIN_X = 4;
    private static final int VIEWPORT_MARGIN_BOTTOM = 8;
    private static final int MIN_THUMB_HEIGHT = 10;

    @Nullable
    private VerticalStack stack;

    public AutoTaskConfigScreen(AutoTaskConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT_MAX;
    }

    @Override
    protected void init() {
        // 自适应屏幕高度：窗口小时不超过屏幕，留 40px 上下边距
        this.imageHeight = Math.min(GUI_HEIGHT_MAX, Math.max(120, this.height - 40));
        super.init();
        int startX = this.leftPos;
        int startY = this.topPos;
        EntityMaid maid = this.menu.getMaid();
        PanelContext ctx = new PanelContext(maid, this.font,
                this.leftPos, this.topPos, this::rebuildWidgets);

        this.stack = new VerticalStack(startX + CONTENT_X_OFFSET, startY + START_Y_OFFSET,
                this.leftPos, this.topPos, this.font, this::addRenderableWidget);
        for (IConfigPanel panel : PanelRegistry.INSTANCE.all()) {
            this.stack.addSpacer(PANEL_SPACER);
            ConfigRow titleRow = this.stack.addRow();
            titleRow.addTitle(panel.getTitle(), TITLE_COLOR);
            panel.buildWidgets(ctx, this.stack);
        }

        // 设置 viewport（屏幕坐标）并完成布局：算内容高度、clamp 偏移、应用到所有行
        int viewportLeft = startX + VIEWPORT_MARGIN_X;
        int viewportRight = startX + this.imageWidth - VIEWPORT_MARGIN_X;
        int viewportTop = startY + START_Y_OFFSET;
        int viewportBottom = startY + this.imageHeight - VIEWPORT_MARGIN_BOTTOM;
        this.stack.setViewport(viewportLeft, viewportTop, viewportRight, viewportBottom);
        this.stack.finishLayout();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            PanelRegistry.INSTANCE.all().forEach(IConfigPanel::commitPending);
            this.setFocused(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 先提交未保存输入，再重建，防丢输入（比各 Panel 各自处理更统一——真善美"善"）。
     * 模式选择变化触发本方法时，RuntimeParamsPanel 的快/慢环 EditBox 值先 commit 再重建。
     */
    @Override
    public void rebuildWidgets() {
        PanelRegistry.INSTANCE.all().forEach(IConfigPanel::commitPending);
        super.rebuildWidgets();
    }

    @Override
    public void removed() {
        PanelRegistry.INSTANCE.all().forEach(IConfigPanel::commitPending);
        super.removed();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.stack != null && this.stack.mouseScrolled(delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int startX = (this.width - this.imageWidth) / 2;
        int startY = (this.height - this.imageHeight) / 2;
        guiGraphics.fill(startX, startY, startX + this.imageWidth, startY + this.imageHeight, BG_COLOR);
    }

    /**
     * 分阶段渲染（不调 super.render）：super 把 bg/widgets/labels/tooltip 绑在一起无法选择性裁剪。
     * widget tooltip 由 renderWithTooltip 在本方法返回后渲染，故不受本方法内 scissor 影响。
     */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Phase 1: 背景（不裁）
        this.renderBg(g, partialTick, mouseX, mouseY);

        // Phase 2: 标题（不裁，固定在 viewport 上方，滚动时不动）
        g.pose().pushPose();
        g.pose().translate(this.leftPos, this.topPos, 0.0F);
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE_COLOR, true);
        g.pose().popPose();

        // Phase 3: widgets + 内容 labels（scissor 裁剪 viewport 外内容）
        boolean clip = this.stack != null && this.stack.hasViewport();
        if (clip) {
            g.enableScissor(this.stack.getViewportLeft(), this.stack.getViewportTop(),
                    this.stack.getViewportRight(), this.stack.getViewportBottom());
        }
        RenderSystem.disableDepthTest();
        for (Renderable renderable : this.renderables) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
        if (this.stack != null) {
            g.pose().pushPose();
            g.pose().translate(this.leftPos, this.topPos, 0.0F);
            this.stack.paintLabels(g);
            g.pose().popPose();
        }
        if (clip) {
            g.disableScissor();
        }

        // Phase 4: 滚动条（不裁，画在 viewport 右侧）
        this.renderScrollbar(g);

        // Phase 5: widget tooltip 由 renderWithTooltip 在 render 返回后渲染（不裁）
    }

    /**
     * 渲染滚动条：内容不足（maxScroll<=0）时不画。
     * thumb 高度 = trackH² / (trackH + maxScroll)，位置按 scrollOffset/maxScroll 比例。
     */
    private void renderScrollbar(GuiGraphics g) {
        if (this.stack == null || !this.stack.hasViewport()) {
            return;
        }
        int maxScroll = this.stack.getMaxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int trackTop = this.stack.getViewportTop();
        int trackBottom = this.stack.getViewportBottom();
        int trackH = trackBottom - trackTop;
        if (trackH <= 0) {
            return;
        }
        int barX = this.stack.getViewportRight() - SCROLLBAR_X_OFFSET;
        int thumbH = Math.max(MIN_THUMB_HEIGHT, trackH * trackH / (trackH + maxScroll));
        int scrollableH = trackH - thumbH;
        int thumbY = trackTop + (maxScroll > 0
                ? scrollableH * this.stack.getScrollOffset() / maxScroll
                : 0);

        // track 底色
        g.fill(barX, trackTop, barX + SCROLLBAR_WIDTH, trackBottom, 0x22FFFFFF);
        // thumb
        g.fill(barX, thumbY, barX + SCROLLBAR_WIDTH, thumbY + thumbH, 0x99FFFFFF);
    }
}
