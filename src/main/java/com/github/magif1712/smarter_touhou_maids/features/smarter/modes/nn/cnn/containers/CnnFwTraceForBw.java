package com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.cnn.containers;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;

/**
 * CNN 前向 trace（供反向使用）：承载单层的 {@code z}（pre-activation）。
 * <p>
 * 重构：删去冗余 {@code y} 字段——StoreTrace 下 {@code trace_y} 即 {@code ys[i]}（调用方注入），
 * 一份写入，extractC/backward 两方消费（设计原则第5条：DPS 式编程，出参由调用方注入）。
 * 原设计中 {@code trace_y} 与 {@code ys[i]} 存同一值 {@code σ(z)} 却分占两缓冲，
 * 中转站 {@code io.getA1()} 还漏写导致 NaN 传播——重构后此 bug 不可能存在。
 * <p>
 * 反向时 σ'(z) = y(1-y)，{@code y}（= {@code ys[i]}）由 {@code bw} 签名直接传入，
 * 不再经本容器中转。{@code z} 保留备用（换 clipped linear 时 backward 需读 {@code z} 判断 {@code |z|<1}）。
 * <p>
 * 资源容器：{@link AutoCloseable}，由 {@code AbstractCnnNeuralNetwork.createFwTraceForBw} 创建，
 * {@code close} 时释放 {@code z}。
 */
public class CnnFwTraceForBw implements AutoCloseable {
    /** pre-activation 累加结果（push atomicAdd + pull_lr + b，未过 σ）。 */
    public final FloatVector z;

    public CnnFwTraceForBw(FloatVector z) {
        this.z = z;
    }

    public FloatVector getZ() {
        return z;
    }

    @Override
    public void close() throws Exception {
        if (z != null) {
            z.close();
        }
    }
}