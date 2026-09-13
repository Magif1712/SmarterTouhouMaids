package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

/**
 * DPS（Destination-Passing Style）出参缓冲区：调用方注入，被调方写入。
 * <p>
 * 设计原则5：function 统一无返回值，出参经参数列表 {@code /*->*&#47;} 右侧注入。
 * 泛型出参无法直接作参数传递（擦除），故实在化为 OutSlot 对象。
 *
 * @param <T> 产出类型
 */
public final class OutSlot<T> {
    private T value;

    public void set(T value) {
        this.value = value;
    }

    /** 供解析器/调用方回收结果。 */
    public T get() {
        return value;
    }
}
