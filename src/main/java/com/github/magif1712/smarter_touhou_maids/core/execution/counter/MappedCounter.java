package com.github.magif1712.smarter_touhou_maids.core.execution.counter;

/**
 * 行为产出代际计数器（generation counter）。
 * <p>
 * 一块 host mapped pinned uint32：GPU 侧在 uranaStream 上（排在写 behavior buffer 之后）
 * 经 atomicAdd_system 递增；host 侧用 {@link #getHostValue()} 普通 load 读取。
 * <p>
 * 设计原则（真善美第 3 条）：把"behavior 是否写完"这个不实在的状态，
 * 用一个 host 直接可见的实在整数固化下来。host 看到 generation 变化，
 * 即意味着 GPU 已执行到递增点 = 此前的 behavior 写入已完成 = mapped buffer 内容完整可读。
 * <p>
 * 整条读取链路零 CUDA 同步调用（无 cudaStreamSynchronize / cudaEventQuery），
 * 不 flush WDDM 命令缓冲，不打乱 uranaStream 的批处理节奏。
 * <p>
 * 设计原则（真善美第 2 条）："宣告产出完成"是 Urana（意识）的模式，
 * 由 UranaSystem 在 runOneTick 末尾调 {@link #increment}；"读取代际判断新鲜度"
 * 是外周消费的模式，由 SmarterClientService 在 onClientTick 调 {@link #getHostValue}。
 */
public final class MappedCounter implements AutoCloseable {

    private long handle;
    private boolean closed = false;

    public MappedCounter() {
        this.handle = CounterNative._create();
        if (this.handle == 0L) {
            throw new RuntimeException("Failed to create native MappedCounter");
        }
    }

    /**
     * 在指定 stream 上入队递增。应排在写 behavior buffer 之后（流内有序）。
     *
     * @param streamHandle CUDA 流句柄（uranaStream）。
     */
    public void increment(long streamHandle) {
        if (closed) {
            throw new IllegalStateException("MappedCounter has been closed");
        }
        CounterNative._increment(handle, streamHandle);
    }

    /**
     * 读取当前 generation 值。纯 host 内存读，零 CUDA 调用，不 flush WDDM 命令缓冲。
     *
     * @return 当前 generation 值（单调递增）。
     */
    public int getHostValue() {
        if (closed) {
            throw new IllegalStateException("MappedCounter has been closed");
        }
        return CounterNative._getHostValue(handle);
    }

    @Override
    public void close() {
        if (!closed) {
            CounterNative._destroy(handle);
            closed = true;
            handle = 0L;
        }
    }
}
