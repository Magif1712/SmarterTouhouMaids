package com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.standard_config_gui.layout;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 垂直堆叠中的一行句柄：提供该行的屏幕坐标供调用方摆放 widget，
 * 并支持在该行注册文字标签（由 {@link VerticalStack#paintLabels} 统一绘制）。
 * <p>
 * 把"label 在 GUI 坐标系、widget 在屏幕坐标系"这个不实在的双坐标系摩擦，
 * 实在化为 ConfigRow 内部的坐标转换（真善美第3条）：
 * 调用方只需给出相对本行原点的 dx/dy 偏移，本类算出 GUI 坐标交给 stack。
 * <p>
 * <b>滚动支持</b>：本行记录构造时的 {@code baseY}（屏幕坐标，scroll 基准）和所有 widget 引用。
 * {@link #applyScroll(int)} 时遍历 widget 调 {@link AbstractWidget#setY(int)} 更新屏幕 y。
 * label 不在此处理——label 在 GUI 坐标系，由 {@link VerticalStack#paintLabels} 统一减 scrollOffset。
 */
@OnlyIn(Dist.CLIENT)
public class ConfigRow {
    private final VerticalStack stack;
    private final int x;
    private final int baseY;
    private final Consumer<AbstractWidget> widgetAdder;
    private final List<AbstractWidget> widgets = new ArrayList<>();

    ConfigRow(VerticalStack stack, int x, int y, Consumer<AbstractWidget> widgetAdder) {
        this.stack = stack;
        this.x = x;
        this.baseY = y;
        this.widgetAdder = widgetAdder;
    }

    /**
     * 在本行注册一个 widget（widget 应在构造时已用 {@link #x()}/{@link #y()} 设好屏幕坐标）。
     * 返回原 widget 以便链式配置。
     */
    public <T extends AbstractWidget> T addWidget(T widget) {
        widgetAdder.accept(widget);
        widgets.add(widget);
        return widget;
    }

    /**
     * 在本行注册一个普通文字标签（无阴影）。
     *
     * @param dx    相对本行原点的 x 偏移
     * @param dy    相对本行原点的 y 偏移（负值=画在 widget 上方）
     * @param color 文字颜色
     */
    public void addLabel(Component label, int dx, int dy, int color) {
        stack.addLabelInternal(label, x - stack.leftPos + dx, baseY - stack.topPos + dy, color, false);
    }

    /**
     * 在本行原点注册一个带阴影的标题（分区/子标题用）。
     */
    public void addTitle(Component label, int color) {
        stack.addLabelInternal(label, x - stack.leftPos, baseY - stack.topPos, color, true);
    }

    public int x() {
        return x;
    }

    public int y() {
        return baseY;
    }

    /**
     * 应用滚动偏移：把本行所有 widget 的屏幕 y 更新为 {@code baseY - scrollOffset}。
     * <p>
     * {@link AbstractWidget#setY(int)} 是 1.20+ 公开 API。
     * setY 已把 widget 推到 viewport 外的屏幕坐标，鼠标在 viewport 内不会命中——无需额外 visible 检查。
     */
    void applyScroll(int scrollOffset) {
        int newY = baseY - scrollOffset;
        for (AbstractWidget w : widgets) {
            w.setY(newY);
        }
    }

    int baseY() {
        return baseY;
    }
}
