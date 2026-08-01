package com.github.magif1712.smarter_touhou_maids.core.execution.counter;

import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

/**
 * MappedCounter 的 JNI 声明。
 * <p>
 * 一个 host mapped pinned uint32 计数器，GPU 经 atomicAdd_system 递增，host 普通读取。
 * 用于把"behavior 是否写完"这个不实在状态固化为 host 直接可见的整数（真善美第 3 条）。
 */
public class CounterNative {
    static {
        NativeLibLoader.ensureLoaded();
    }

    public static native long _create();

    public static native void _destroy(long handle);

    public static native void _increment(long handle, long streamHandle);

    public static native int _getHostValue(long handle);
}
