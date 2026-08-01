package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.mapping;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.IntVector;

/**
 * BNN 特定原生操作的包装类：negateAndBinarize（IntVector 梯度 → BoolVector 输入）。
 * <p>
 * 设计原则（真善美第2条）：此操作是 BNN 载体特定的（int 梯度取反二值化为 bit 输入），
 * 属于 nn/bnn 实现层，不暴露给 urana。urana 通过 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork#gradientToInput}
 * 间接调用。
 */
public class BnnOps {

    /**
     * 对一个整数向量进行原地取反和二值化，并将结果存入一个布尔向量。
     *
     * @param srcIntVector  源整数向量。
     * @param dstBoolVector 目标布尔向量。
     * @param streamHandle  CUDA 流句柄（kernel 在此流上执行）。
     */
    public static void negateAndBinarize(IntVector srcIntVector, BoolVector dstBoolVector, long streamHandle) {
        if (srcIntVector.size() != dstBoolVector.size()) {
            throw new IllegalArgumentException("Source and destination vectors must have the same size.");
        }
        negateAndBinarizeRegion(
                dstBoolVector, new Span(0, dstBoolVector.size()) {},
                srcIntVector, new Span(0, srcIntVector.size()) {},
                streamHandle
        );
    }

    /**
     * 对整数向量的一个子区域进行取反和二值化，并将结果存入布尔向量的对应子区域。
     *
     * @param dst          目标布尔向量。
     * @param dstSpan      目标向量中的区域。
     * @param src          源整数向量。
     * @param srcSpan      源向量中的区域。
     * @param streamHandle CUDA 流句柄。
     */
    public static void negateAndBinarizeRegion(BoolVector dst, Span dstSpan, IntVector src, Span srcSpan, long streamHandle) {
        if (dstSpan.getLength() != srcSpan.getLength()) {
            throw new IllegalArgumentException("Source and destination spans must have the same length.");
        }
        if (dstSpan.getOffset() + dstSpan.getLength() > dst.size()) {
            throw new IndexOutOfBoundsException("Destination span is out of bounds.");
        }
        if (srcSpan.getOffset() + srcSpan.getLength() > src.size()) {
            throw new IndexOutOfBoundsException("Source span is out of bounds.");
        }

        BnnOpsNative._negateAndBinarizeRegion(
                dst.requireHandle(),
                dstSpan.getOffset(),
                src.requireHandle(),
                srcSpan.getOffset(),
                srcSpan.getLength(),
                streamHandle
        );
    }
}
