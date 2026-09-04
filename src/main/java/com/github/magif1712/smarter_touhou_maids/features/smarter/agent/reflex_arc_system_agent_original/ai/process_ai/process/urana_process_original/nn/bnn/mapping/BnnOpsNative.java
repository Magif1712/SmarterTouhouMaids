package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.mapping;

import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

class BnnOpsNative {
    static {
        NativeLibLoader.ensureLoaded();
    }

    /**
     * Native method to perform negate and binarize operation on vectors.
     * It takes the handles (pointers) to the native vectors.
     *
     * @param dstVectorBoolHandle The handle to the destination BoolVector.
     * @param srcVectorIntHandle  The handle to the source IntVector.
     * @param streamHandle        CUDA stream handle.
     */
    public static native void _negateAndBinarize(long dstVectorBoolHandle, long srcVectorIntHandle, long streamHandle);

    /**
     * Native method to perform negate and binarize operation on a region of vectors.
     *
     * @param dst_handle    The handle to the destination BoolVector.
     * @param dst_offset    The starting bit offset in the destination vector.
     * @param src_handle    The handle to the source IntVector.
     * @param src_offset    The starting element offset in the source vector.
     * @param n             The number of elements to process.
     * @param streamHandle  CUDA stream handle.
     */
    public static native void _negateAndBinarizeRegion(long dst_handle, long dst_offset, long src_handle, long src_offset, long n, long streamHandle);
}
