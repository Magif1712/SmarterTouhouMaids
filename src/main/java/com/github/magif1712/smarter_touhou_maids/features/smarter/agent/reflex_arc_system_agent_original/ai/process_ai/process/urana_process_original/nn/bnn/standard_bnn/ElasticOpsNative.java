package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.standard_bnn;

import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

/**
 * 输入变化门控重连的 native 操作声明。
 * <p>
 * 惊跳反射（startle reflex）：当输入位发生变化时，重连对应的断连 q 通道。
 * 静止期不触发重连（AI 安静睡眠），环境变化时精确重连（AI 苏醒）。
 * <p>
 * 设计原则（真善美第4条）：把"醒不过来"这个不实在的问题，
 * 实在化为可调用的 native 重连操作——输入变化时精确恢复信号通路。
 */
public class ElasticOpsNative {
    static {
        NativeLibLoader.ensureLoaded();
    }

    /**
     * 输入变化门控重连：当输入位发生变化时，重连对应的断连 q 通道。
     * <p>
     * 对每个位 i：
     *   changed[i] = currentInput[i] XOR prevInput[i]
     *   if changed[i] AND q[i] == 0: q[i] = 1
     * 调用后 prevInput 会被更新为 currentInput 的副本。
     *
     * @param inputHandle     当前输入 BoolVector 的 native 句柄
     * @param prevInputHandle 上一步输入 BoolVector 的 native 句柄（调用后更新）
     * @param qHandle         q 权重 BoolVector 的 native 句柄（原地修改）
     * @param streamHandle    CUDA 流句柄
     */
    public static native void _reconnectOnInputChange(long inputHandle, long prevInputHandle,
                                                       long qHandle, long streamHandle);
}
