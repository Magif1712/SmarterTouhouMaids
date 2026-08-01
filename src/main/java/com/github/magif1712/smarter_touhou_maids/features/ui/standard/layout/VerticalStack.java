package com.github.magif1712.smarter_touhou_maids.features.ui.standard.layout;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 垂直堆叠布局原语：把"手动累加 nextY += 25 追踪坐标"这个不实在的东西，
 * 实在化为"自动推进 y + label/widget 绑定"（真善美第3条）。
 * <p>
 * Panel 通过 {@link #addRow()} 取得行句柄后摆放 widget 与 label；
 * Screen 在 renderLabels 调 {@link #paintLabels} 统一绘制所有 label，
 * 消除原 modeEndYGui 手算与 label/widget 分离。
 * <p>
 * <b>双坐标系</b>（实在化核心）：
 * <ul>
 *   <li>widget 用屏幕坐标（{@code leftPos/topPos} 偏移后的绝对坐标）。</li>
 *   <li>label 用 GUI 坐标（相对屏幕左上角的坐标，{@code renderLabels} 已平移到 {@code leftPos/topPos}）。</li>
 *   <li>本类持 {@code leftPos/topPos} 做 GUI 坐标转换，调用方只管相对偏移。</li>
 * </ul>
 * <p>
 * <b>滚动</b>（把"内容溢出 viewport"这个不实在的约束，实在化为三量关系）：
 * <ul>
 *   <li>{@code viewportTopScreen}/{@code viewportBottomScreen}：可视区域上下界（屏幕坐标，scissor 用）。</li>
 *   <li>{@code scrollOffset}：当前向下滚动的像素数（0=顶）。</li>
 *   <li>{@code contentHeight}：全部内容的总高度。</li>
 *   <li>{@code maxScroll = max(0, contentHeight - viewportHeight)}：可滚上限。</li>
 * </ul>
 * 滚动时 widget 走 {@link ConfigRow#applyScroll}（setY 改屏幕坐标，自动推出 viewport 外不响应点击），
 * label 走 {@link #paintLabels} 内 {@code e.y - scrollOffset}（GUI 坐标减偏移）。
 */
@OnlyIn(Dist.CLIENT)
public class VerticalStack {
    private static final int DEFAULT_ROW_HEIGHT = 20;

    final int leftPos;
    final int topPos;
    private final int contentX;
    private final Font font;
    private final Consumer<AbstractWidget> widgetAdder;
    private final List<LabelEntry> labels = new ArrayList<>();
    private final List<ConfigRow> rows = new ArrayList<>();
    private final int startY;
    private int y;

    // viewport（屏幕坐标 scissor 边界）。未设置时视为无界（不裁不滚）。
    // left/right 供 scissor 横向裁剪，top/bottom 供 scissor 纵向裁剪与滚动量计算。
    private int viewportLeftScreen = Integer.MIN_VALUE;
    private int viewportRightScreen = Integer.MAX_VALUE;
    private int viewportTopScreen = Integer.MIN_VALUE;
    private int viewportBottomScreen = Integer.MAX_VALUE;
    private int scrollOffset = 0;
    private int contentHeight = 0;

    public VerticalStack(int contentX, int startY, int leftPos, int topPos, Font font,
                         Consumer<AbstractWidget> widgetAdder) {
        this.contentX = contentX;
        this.y = startY;
        this.startY = startY;
        this.leftPos = leftPos;
        this.topPos = topPos;
        this.font = font;
        this.widgetAdder = widgetAdder;
    }

    /**
     * 追加一个空行（占位推进 y），返回该行句柄。
     * 调用方据 {@link ConfigRow#x()}/{@link ConfigRow#y()} 摆放 widget 与 label。
     */
    public ConfigRow addRow() {
        ConfigRow row = new ConfigRow(this, contentX, y, widgetAdder);
        rows.add(row);
        y += DEFAULT_ROW_HEIGHT;
        return row;
    }

    /** 追加固定高度的空白（分区之间的间隔）。 */
    public void addSpacer(int height) {
        y += height;
    }

    /** 当前已堆叠到的 y（屏幕坐标），供 Screen 算总内容高度。 */
    public int currentY() {
        return y;
    }

    /**
     * 设置可视区域（屏幕坐标，四边）。init 末尾调用。
     * <p>
     * viewport 即 scissor 裁剪框：widget/label 超出此区域被裁。
     * left/right 用于横向裁剪（含右侧滚动条占位），top/bottom 用于纵向裁剪与滚动量计算。
     * 不设置时无界（用于不滚动的场景）。
     */
    public void setViewport(int left, int top, int right, int bottom) {
        this.viewportLeftScreen = left;
        this.viewportRightScreen = right;
        this.viewportTopScreen = top;
        this.viewportBottomScreen = bottom;
    }

    /**
     * 完成布局：计算内容高度、clamp 滚动偏移、应用到所有行。
     * <p>
     * init 末尾或 rebuild 后调用。重建后 stack 是新实例，scrollOffset 归零，自动回顶。
     */
    public void finishLayout() {
        this.contentHeight = this.y - this.startY;
        clampScroll();
        applyScrollToRows();
    }

    /**
     * 处理鼠标滚轮：更新 scrollOffset 并应用到所有行。
     * <p>
     * Minecraft 1.20 的 mouseScrolled 传 {@code delta}（向上正、向下负，单位为"行"×阈值）。
     * 本方法按像素滚动（每行 delta 折算为若干像素）。
     *
     * @return true 若消费了事件（内容可滚且 offset 变化）
     */
    public boolean mouseScrolled(double delta) {
        if (getMaxScroll() <= 0) {
            return false;
        }
        int old = this.scrollOffset;
        // delta 量级与 Minecraft 输入灵敏度相关，放大为像素
        int pixelDelta = -(int) Math.round(delta * DEFAULT_ROW_HEIGHT * 2);
        this.scrollOffset = Math.max(0, Math.min(getMaxScroll(), this.scrollOffset + pixelDelta));
        if (this.scrollOffset != old) {
            applyScrollToRows();
            return true;
        }
        return false;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public int getMaxScroll() {
        int viewportH = viewportBottomScreen - viewportTopScreen;
        if (viewportH <= 0 || viewportTopScreen == Integer.MIN_VALUE) {
            return 0;
        }
        return Math.max(0, contentHeight - viewportH);
    }

    public int getViewportTop() {
        return viewportTopScreen;
    }

    public int getViewportBottom() {
        return viewportBottomScreen;
    }

    public int getViewportLeft() {
        return viewportLeftScreen;
    }

    public int getViewportRight() {
        return viewportRightScreen;
    }

    /** viewport 是否已设置（未设置则不裁不滚）。 */
    public boolean hasViewport() {
        return viewportTopScreen != Integer.MIN_VALUE;
    }

    /** 在 renderLabels 中调用，统一绘制所有已注册的 label（GUI 坐标系，减滚动偏移）。 */
    public void paintLabels(GuiGraphics g) {
        int viewportTopGui = viewportTopScreen - topPos;
        int viewportBottomGui = viewportBottomScreen - topPos;
        for (LabelEntry e : labels) {
            int drawY = e.y - scrollOffset;
            // 跳过 viewport 外 label（避免画到 scissor 外被裁的视觉残留）
            if (viewportTopScreen != Integer.MIN_VALUE && (drawY < viewportTopGui - 12 || drawY > viewportBottomGui)) {
                continue;
            }
            g.drawString(font, e.text, e.x, drawY, e.color, e.shadow);
        }
    }

    void addLabelInternal(Component text, int guiX, int guiY, int color, boolean shadow) {
        labels.add(new LabelEntry(text, guiX, guiY, color, shadow));
    }

    private void clampScroll() {
        int max = getMaxScroll();
        if (this.scrollOffset > max) {
            this.scrollOffset = max;
        }
        if (this.scrollOffset < 0) {
            this.scrollOffset = 0;
        }
    }

    private void applyScrollToRows() {
        for (ConfigRow row : rows) {
            row.applyScroll(scrollOffset);
        }
    }

    private static final class LabelEntry {
        final Component text;
        final int x;
        final int y;
        final int color;
        final boolean shadow;

        LabelEntry(Component text, int x, int y, int color, boolean shadow) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.shadow = shadow;
        }
    }
}
