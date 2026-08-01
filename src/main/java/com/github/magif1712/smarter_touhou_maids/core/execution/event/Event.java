package com.github.magif1712.smarter_touhou_maids.core.execution.event;

import com.github.magif1712.smarter_touhou_maids.core.execution.stream.Stream;

/**
 * 表示一个 CUDA Event，用于跨流的 GPU 侧同步。
 * <p>
 * 设计原则（真善美第 3 条）：把"视觉是否写完 feelingBuffer"这个不实在的状态，
 * 用一个实在的标记对象固化下来。视觉 kernel 跑完后在 cudaStream 上 {@link #record}，
 * Urana 在自己的 uranaStream 上 {@link Stream#waitEvent} 等待，由 GPU 自行排队，CPU 不阻塞。
 * <p>
 * 典型用法（vision → Urana 跨流同步）：
 * <pre>
 *   // 视觉侧（cudaStream 上提交 vision kernel 后）
 *   visionEvent.record(cudaStream.getHandle());
 *   // Urana 侧（runOneTick 开头，读 feelingBuffer 前）
 *   uranaStream.waitEvent(visionEvent);
 * </pre>
 */
public final class Event implements AutoCloseable {

    private long handle;
    private boolean isClosed = false;

    /**
     * Creates a new CUDA event.
     */
    public Event() {
        this.handle = EventNative._createEvent();
    }

    /**
     * Gets the native handle of the CUDA event.
     *
     * @return The native handle (pointer) of the event.
     * @throws IllegalStateException if the event has already been closed.
     */
    public long getHandle() {
        if (isClosed) {
            throw new IllegalStateException("Event has been closed.");
        }
        return handle;
    }

    /**
     * 在指定 stream 上记录该 event。record 后，其它 stream 可通过 {@link Stream#waitEvent} 等待此 event。
     *
     * @param streamHandle 要在其上 record 的 stream 句柄（0 表示 NULL 流）。
     */
    public void record(long streamHandle) {
        if (isClosed) {
            throw new IllegalStateException("Event has been closed.");
        }
        EventNative._recordEvent(handle, streamHandle);
    }

    /**
     * Destroys the underlying CUDA event and releases its resources.
     */
    @Override
    public void close() {
        if (!isClosed) {
            EventNative._destroyEvent(handle);
            isClosed = true;
            handle = 0;
        }
    }
}
