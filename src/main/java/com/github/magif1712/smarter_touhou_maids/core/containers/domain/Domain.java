package com.github.magif1712.smarter_touhou_maids.core.containers.domain;

public abstract class Domain<T> {
    /**
     * 唯一本质：此元素是否属于该域的可能性范围。
     * 没有维度，没有起点，没有大小，没有结构。
     */
    public abstract boolean contains(T element);
}
