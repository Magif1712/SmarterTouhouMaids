package com.github.magif1712.smarter_touhou_maids.core.execution;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 刷新请求（host 原子标志）：消费者请求新鲜数据，生产者取走后满足。
 * <p>
 * 拉模型握手原语：生产频率高于消费频率时，生产者每轮 {@link #consume()} 检查——
 * 有请求才生产（并清除请求），无请求跳过。保证恒等式"生产次数 ≤ 消费次数"，
 * 生产永不超出实际需求。
 * <p>
 * 纯技术原语，无域语义——域语义活在注入点的变量名（如 feelingRefresh）。
 * 同 {@link MappedGenerationBuffer} 先例：放 core/execution，任何分支可用。
 * <p>
 * 线程安全：request/consume 各一次原子操作，无锁、无 CUDA 调用、无 WDDM flush。
 * 跨线程可见性由 AtomicBoolean 的 volatile 语义保证。
 */
public final class RefreshRequest {

    private final AtomicBoolean requested;

    /**
     * 构造即置位：首次生产无条件满足一次，消费者首轮即有有效数据
     * （避免消费者首轮读到未初始化的缓冲）。
     */
    public RefreshRequest() {
        this.requested = new AtomicBoolean(true);
    }

    /**
     * 消费者：请求刷新（幂等，多次请求合并为一次）。
     */
    public void request() {
        requested.set(true);
    }

    /**
     * 生产者：取走请求（有请求返回 true 并清除，无请求返回 false）。
     */
    public boolean consume() {
        return requested.getAndSet(false);
    }
}
