package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.containers;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;

/**
 * CNN 前向 trace（供反向使用）：承载单层的 {@code z}（pre-activation）与 {@code y}（activation）。
 * <p>
 * 与 BNN 的 {@code fz} 前向存储模式对称：前向 kernel 写 {@code z+y}，反向 kernel 读 {@code y}。
 * <p>
 * 反向时 σ'(z) = y(1-y)，故 {@code y} 是反向的充分信息（δ_j = 2(y_j - y'_j) · y_j(1-y_j)）。
 * {@code z} 存储备用（数值检查/调试），反向 kernel 不直接读——σ'(z) 用 {@code y} 即可，
 * 避免重复计算 σ(z) 的浮点误差。
 * <p>
 * 资源容器：{@link AutoCloseable}，由 {@code AbstractCnnNeuralNetwork.createFwTraceForBw} 创建，
 * {@code close} 时释放 {@code z/y}。
 */
public class CnnFwTraceForBw implements AutoCloseable {
    /** pre-activation 累加结果（push atomicAdd + pull_lr + b，未过 σ）。 */
    public final FloatVector z;
    /** activation = σ(z)，前向最终输出，反向充分信息。 */
    public final FloatVector y;

    public CnnFwTraceForBw(FloatVector z, FloatVector y) {
        this.z = z;
        this.y = y;
    }

    public FloatVector getZ() {
        return z;
    }

    public FloatVector getY() {
        return y;
    }

    @Override
    public void close() throws Exception {
        if (z != null) {
            z.close();
        }
        if (y != null) {
            y.close();
        }
    }
}
