package com.github.magif1712.smarter_touhou_maids.core.execution.event;

/**
 * JNI bridge for CUDA event operations.
 * This class is package-private and should not be used directly by features.
 * All native calls related to events are centralized here.
 */
import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

class EventNative {
    static {
        NativeLibLoader.ensureLoaded();
    }

    /**
     * Creates a new CUDA event.
     *
     * @return The native handle (pointer) to the created event.
     */
    static native long _createEvent();

    /**
     * Records the event on the given stream. After recording, other streams can wait on it.
     *
     * @param eventHandle  The handle of the event to record.
     * @param streamHandle The handle of the stream on which to record (0 for NULL stream).
     */
    static native void _recordEvent(long eventHandle, long streamHandle);

    /**
     * Destroys a CUDA event and releases its resources.
     *
     * @param eventHandle The handle of the event to destroy.
     */
    static native void _destroyEvent(long eventHandle);
}
