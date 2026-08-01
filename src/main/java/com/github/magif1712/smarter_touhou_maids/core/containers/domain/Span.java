package com.github.magif1712.smarter_touhou_maids.core.containers.domain;

public abstract class Span extends Domain<Integer> {
    private final int offset;
    private final int length;

    public Span(int offset, int length) {
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("Offset and length must be non-negative.");
        }
        this.offset = offset;
        this.length = length;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public int size() {
        return length;
    }

    /**
     * 判断一个索引是否落在此 Span 定义的区间内。
     * 区间为 [offset, offset + length)。
     * @param index 要检查的索引。
     * @return 如果索引在区间内，则为 true，否则为 false。
     */
    @Override
    public boolean contains(Integer index) {
        if (index == null) {
            return false;
        }
        return index >= offset && index < offset + length;
    }
}
