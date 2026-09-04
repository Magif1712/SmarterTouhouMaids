package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.effector.bionic_muscle_effector.muscle;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.effector.bionic_muscle_effector.semantics.MuscleGroupDescriptor;

/**
 * 运动单元池：一组 bit → 连续激活强度。
 * <p>
 * 生物对应：一块肌肉由许多运动单元支配，单个运动单元是“全或无”的（1 bit），
 * 但整块肌肉的张力是所有运动单元放电的加权和。单根 bit 翻转只让强度变化 1/N，
 * 不改变肌肉本质——这是空间容错（重复码 + 软判决）。
 * <p>
 * 解码：加权求和 {@code sum(bits)/N}，输出 [0,1] 连续激活强度。
 * 再经 {@link TensionIntegrator} 低通滤波（时间容错），输出平滑张力。
 * <p>
 * 有状态：每实例持有一个 {@link TensionIntegrator}，对应一块肌肉的当前张力。
 */
public class MuscleGroup {

    private final MuscleGroupDescriptor descriptor;
    private final TensionIntegrator tension;

    public MuscleGroup(MuscleGroupDescriptor descriptor, float alpha) {
        this.descriptor = descriptor;
        this.tension = new TensionIntegrator(alpha);
    }

    /**
     * 从 bit-packed 行为向量解码本肌群的激活强度，并更新张力。
     *
     * @param packedBehavior bit-packed 行为向量（256 bits = 8 个 int，LSB-first）。
     * @return 平滑后的张力 [0,1]。
     */
    public float tick(int[] packedBehavior) {
        float activation = decodeActivation(packedBehavior);
        return tension.update(activation);
    }

    /**
     * 加权求和：本肌群位段内 set 位数 / 位段长度。
     * 单 bit 翻转只让结果变化 1/N，单 bit 误码不翻转判决。
     */
    private float decodeActivation(int[] packedBehavior) {
        int offset = descriptor.getOffset();
        int length = descriptor.getLength();
        int count = 0;
        for (int i = 0; i < length; i++) {
            if (getBit(packedBehavior, offset + i)) {
                count++;
            }
        }
        return (float) count / length;
    }

    /**
     * 读取 bit-packed 数组的第 bitIndex 位（LSB-first：bit i 在 int[i/32] 的 (i%32) 位）。
     * 与 BoolVector.copyToHost 的打包顺序一致。
     */
    private static boolean getBit(int[] packed, int bitIndex) {
        int wordIndex = bitIndex / 32;
        int bitInWord = bitIndex % 32;
        return (packed[wordIndex] & (1 << bitInWord)) != 0;
    }

    public float getTension() {
        return tension.getCurrent();
    }

    public MuscleGroupDescriptor getDescriptor() {
        return descriptor;
    }

    public void reset() {
        tension.reset();
    }
}
