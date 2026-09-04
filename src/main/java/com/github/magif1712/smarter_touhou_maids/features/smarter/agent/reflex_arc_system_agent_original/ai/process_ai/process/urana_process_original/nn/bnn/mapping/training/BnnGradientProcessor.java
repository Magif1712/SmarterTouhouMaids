package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.mapping.training;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorMappings;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.io.gradient.BnnOutputLayerGradient;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.io.value.BnnOutputVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.io.value.BnnTargetVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.IntVector;

import java.util.Objects;

/**
 * 一个处理器类，封装了与神经网络梯度计算相关的算法。
 * <p>
 * 这个类遵循单一职责原则，将复杂的计算逻辑从数据容器中分离出来，
 * 使得代码更加模块化、可测试和易于维护。
 */
public class BnnGradientProcessor {

    /**
     * 根据实际输出和目标输出，计算输出层指定子区间的梯度。
     *
     * @param actualOutput 网络的实际输出 (a_L)。
     * @param target       期望的目标 (y)。
     * @param outGradient  用于存储计算结果的梯度容器。
     * @param span         要计算梯度的子区间。
     * @param streamHandle CUDA 流句柄（减法/乘法 kernel 在此流上执行）。
     */
    public static void calculateOutputLayerGradient(BnnOutputVector actualOutput, BnnTargetVector target, BnnOutputLayerGradient outGradient, Span span, long streamHandle) {
        // 步骤 1: 健壮性检查
        Objects.requireNonNull(actualOutput, "实际输出向量不能为空。");
        Objects.requireNonNull(target, "目标向量不能为空。");
        Objects.requireNonNull(outGradient, "输出梯度容器不能为空。");
        Objects.requireNonNull(span, "计算区间不能为空。");

        // 步骤 2: 从具体类获取底层的设备数组
        BoolVector actualOutputVector = actualOutput.getVector();
        BoolVector targetVec = target.getVector();
        IntVector gradientArray = outGradient.getVector();

        // 步骤 3: 检查向量大小和区间有效性
        if (actualOutputVector.size() != targetVec.size() || actualOutputVector.size() != gradientArray.size()) {
            throw new IllegalArgumentException("实际向量、目标向量和输出梯度向量必须具有相同的大小。");
        }
        if (span.getOffset() < 0 || span.getLength() < 0 || span.getOffset() + span.getLength() > actualOutputVector.size()) {
            throw new IllegalArgumentException("指定的计算区间超出了向量边界。");
        }
        if (span.getLength() == 0) {
            return; // 区间为空，无需计算
        }

        // 步骤 4: 执行核心计算的第一部分：向量减法
        // 计算 (a_L - y) 并将结果存储在 gradientArray 的指定区间
        VectorMappings.subtractBool(gradientArray, span, actualOutputVector, targetVec, span, streamHandle);

        // 步骤 5: 执行核心计算的第二部分：标量乘法
        // 对 gradientArray 的指定区间执行原地乘以 2 的操作
        gradientArray.multiplyByScalar(2, span, streamHandle);
    }
}