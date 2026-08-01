package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug;

import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 一项可调试开关的数据描述：显示文本 + 提示 + 读取 + 写入。
 * <p>
 * 把"Agent 有哪些调试项"这个不实在的集合，实在化为 {@link DebugOption} 的列表
 * （真善美第3条）。GUI（AgentDebugPanel）只消费本数据结构渲染 CycleButton，
 * 不感知调试项背后的具体实现（VisionDebugHook / static dt / ...）。
 * <p>
 * <b>不可变</b>：label/tooltip/getter/setter 构造后只读。getter/setter 转发到
 * 具体调试状态存储（static 字段 / 单例），本类不持有也不改存储——范围 B 约束：
 * 调试状态存储保持现状，本类仅作"视图"封装。
 */
public final class DebugOption {
    private final Component label;
    @Nullable
    private final Component tooltip;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;

    public DebugOption(Component label, @Nullable Component tooltip,
                        BooleanSupplier getter, Consumer<Boolean> setter) {
        this.label = label;
        this.tooltip = tooltip;
        this.getter = getter;
        this.setter = setter;
    }

    /** 便捷工厂：on/off 开关型调试项。 */
    public static DebugOption onOff(Component label, @Nullable Component tooltip,
                                    BooleanSupplier getter, Consumer<Boolean> setter) {
        return new DebugOption(label, tooltip, getter, setter);
    }

    public Component label() {
        return label;
    }

    @Nullable
    public Component tooltip() {
        return tooltip;
    }

    public boolean get() {
        return getter.getAsBoolean();
    }

    public void set(boolean value) {
        setter.accept(value);
    }
}
