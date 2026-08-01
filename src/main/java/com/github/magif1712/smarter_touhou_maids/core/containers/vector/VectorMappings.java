package com.github.magif1712.smarter_touhou_maids.core.containers.vector;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;

import java.util.Objects;

/**
 * 向量运算映射类，提供向量之间的高级操作。
 */
public final class VectorMappings {
    /**
     * 将源位向量 src 中的每一位按照索引映射 P 分散写入目标位向量 dst。
     */
    public static void scatterBits(BoolVector src, BoolVector dst, IntVector P) {
        Objects.requireNonNull(src);
        Objects.requireNonNull(dst);
        Objects.requireNonNull(P);
        if (src.size() != P.size()) {
            throw new IllegalArgumentException("Source vector and mapping vector must have the same size.");
        }
        VectorNative._scatterBits(src.requireHandle(), dst.requireHandle(), P.requireHandle());
    }

    /**
     * 对两个位压缩向量进行 XOR 操作 (dst ^= src)。
     */
    public static void xorBool(BoolVector dst, BoolVector src) {
        Objects.requireNonNull(dst);
        Objects.requireNonNull(src);
        if (dst.size() != src.size()) {
            throw new IllegalArgumentException("Vector sizes must match for XOR operation.");
        }
        VectorNative._xorBool(dst.requireHandle(), src.requireHandle());
    }

    /**
     * 计算两个位向量的差值 (c = a - b)，结果存储在整数向量中。
     * 使用 Span 定义操作区间。
     *
     * @param streamHandle CUDA 流句柄（kernel 在此流上执行）。
     */
    public static void subtractBool(IntVector c, Span cSpan, BoolVector a, BoolVector b, Span abSpan, long streamHandle) {
        Objects.requireNonNull(c);
        Objects.requireNonNull(cSpan);
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);
        Objects.requireNonNull(abSpan);

        if (cSpan.getLength() != abSpan.getLength()) {
            throw new IllegalArgumentException("Source and destination spans must have the same length.");
        }

        VectorNative._subtractBool(
                a.requireHandle(),
                b.requireHandle(),
                abSpan.getOffset(),
                c.requireHandle(),
                cSpan.getOffset(),
                abSpan.getLength(),
                streamHandle
        );
    }
}