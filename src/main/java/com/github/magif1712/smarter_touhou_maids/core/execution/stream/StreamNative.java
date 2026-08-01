package com.github.magif1712.smarter_touhou_maids.core.execution.stream;

/**
 * JNI bridge for CUDA stream operations.
 * This class is package-private and should not be used directly by features.
 * All native calls related to streams are centralized here.
 */
import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

class StreamNative {
    static {
        NativeLibLoader.ensureLoaded();
    }

    /**
     * Calls the native method to create a new CUDA stream.
     *
     * @return The native handle (pointer) to the created stream.
     */
    static native long _createStream();

    /**
     * Calls the native method to destroy a CUDA stream.
     *
     * @param streamHandle The handle of the stream to destroy.
     */
    static native void _destroyStream(long streamHandle);

    /**
     * Calls the native method to synchronize a CUDA stream.
     *
     * @param streamHandle The handle of the stream to synchronize.
     */
    static native void _synchronize(long streamHandle);

    /**
     * 让指定 stream 等待指定 event 完成（GPU 侧等待，不阻塞 CPU）。
     *
     * @param streamHandle 等待方 stream 句柄。
     * @param eventHandle  被等待的 event 句柄。
     */
    static native void _waitEvent(long streamHandle, long eventHandle);
}