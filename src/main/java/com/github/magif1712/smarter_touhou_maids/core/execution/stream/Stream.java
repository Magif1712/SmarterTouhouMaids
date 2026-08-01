package com.github.magif1712.smarter_touhou_maids.core.execution.stream;

import com.github.magif1712.smarter_touhou_maids.core.execution.event.Event;

/**
 * Represents a CUDA stream, which is a sequence of operations that execute in order.
 * This class provides a high-level, AutoCloseable interface for managing the lifecycle of a native CUDA stream.
 * <p>
 * A Stream object is a handle to the underlying CUDA stream. It does not hold any tasks or data itself;
 * it is merely a context identifier passed to asynchronous operations to specify their execution mode.
 */
public final class Stream implements AutoCloseable {

    private long handle;
    private boolean isClosed = false;

    /**
     * Creates a new CUDA stream.
     * This corresponds to creating a new, independent execution path on the GPU.
     */
    public Stream() {
        this.handle = StreamNative._createStream();
    }

    /**
     * Gets the native handle of the CUDA stream.
     * This handle is used to identify the stream when passed to native methods.
     *
     * @return The native handle (pointer) of the stream.
     * @throws IllegalStateException if the stream has already been closed.
     */
    public long getHandle() {
        if (isClosed) {
            throw new IllegalStateException("Stream has been closed.");
        }
        return handle;
    }

    /**
     * Blocks the calling thread until all previously issued commands in this stream have completed.
     */
    public void synchronize() {
        if (isClosed) {
            throw new IllegalStateException("Stream has been closed.");
        }
        StreamNative._synchronize(handle);
    }

    /**
     * 让本 stream 等待指定 event 完成（GPU 侧等待，不阻塞 CPU）。
     * <p>
     * 典型用于跨流同步：在 stream A 上 {@link Event#record}，在 stream B 上 waitEvent(event)，
     * 则 B 上后续操作会等 A 上 record 之前的操作完成。
     *
     * @param event 要等待的 event。
     */
    public void waitEvent(Event event) {
        if (isClosed) {
            throw new IllegalStateException("Stream has been closed.");
        }
        StreamNative._waitEvent(handle, event.getHandle());
    }

    /**
     * Destroys the underlying CUDA stream and releases its resources.
     * This method is automatically called when the Stream is used in a try-with-resources statement.
     */
    @Override
    public void close() {
        if (!isClosed) {
            StreamNative._destroyStream(handle);
            isClosed = true;
            handle = 0; // Invalidate the handle
        }
    }
}