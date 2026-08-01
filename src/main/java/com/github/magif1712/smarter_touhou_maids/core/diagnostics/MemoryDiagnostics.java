package com.github.magif1712.smarter_touhou_maids.core.diagnostics;

import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

/**
 * GPU 显存诊断。通过 JNI 调用 cudaMemGetInfo 查询当前 GPU 显存使用情况。
 * <p>
 * 该查询为轻量级 driver 查询，不启动内核、不搬运数据、不强制同步 GPU 工作流，
 * 单次开销在微秒量级，可在每 tick 调用而不影响性能。
 */
public class MemoryDiagnostics {
    static {
        NativeLibLoader.ensureLoaded();
    }

    /**
     * 查询当前 GPU 显存信息。
     *
     * @return long[2] = {freeBytes, totalBytes}；查询失败时返回 {0, 0}
     */
    public static native long[] _getMemInfo();
}
