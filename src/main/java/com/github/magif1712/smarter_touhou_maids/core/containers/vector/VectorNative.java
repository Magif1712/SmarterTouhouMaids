package com.github.magif1712.smarter_touhou_maids.core.containers.vector;

import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

/**
 * JNI 桥接类的 Java 声明，用于 Vector (向量) 的原生操作。
 * <p>
 * 流参数约定（真善美：D2D/H2D 拷贝与计算 kernel 显式接受 stream，不再隐式依赖 NULL 流）：
 * <ul>
 *   <li>D2D/H2D 拷贝与计算 kernel 方法均带 {@code long streamHandle} 末位参数，
 *       由调用方显式传入目标流（Urana 内部传 uranaStream，构造期一次性初始化传 0L）。</li>
 *   <li>{@code _copyToHostBool}/{@code _copyToHostInt} 故意不带 stream：
 *       它们是同步的 H2D 读取（cudaMemcpy，NULL 流），需阻塞 CPU 等待结果，语义上必须同步。
 *       注意：NULL 流同步会 drain 其它流（含 GL 渲染流），不可在主渲染线程调用。</li>
 *   <li>{@code _copyToHostBoolSync} 带 stream：指定流上的同步 D2H
 *       （cudaMemcpyAsync + cudaStreamSynchronize），单流同步不 drain 其它流。
 *       注：behavior 通路已改用 mapped pinned memory 零拷贝直读（MappedGenerationBuffer），
 *       此方法当前无调用方，保留备用于需要显式同步 D2H 的场景。</li>
 * </ul>
 */
public class VectorNative {
    static {
        NativeLibLoader.ensureLoaded();
    }

    // ====================================================================
    // Vector<bool> (位压缩) 原生方法
    // ====================================================================
    public static native long _createVectorBool();

    public static native void _allocateBool(long handle, int size);

    public static native void _deleteVectorBool(long handle);

    public static native void _copyFromHostBool(long handle, int[] data, int wordCount, long streamHandle);

    public static native void _copyToHostBool(long handle, int[] data, int wordCount);

    /**
     * 指定 stream 上的同步 D2H（cudaMemcpyAsync + cudaStreamSynchronize）。
     * <p>
     * 单流同步：只等 {@code streamHandle} 的先前工作，不 drain 其它流（如 GL 渲染流）。
     * 注意：behavior 通路已改用 mapped pinned memory 零拷贝直读（MappedGenerationBuffer），
     * 此方法当前无调用方，保留备用于需要显式同步 D2H 的场景。
     * {@code streamHandle=0}（NULL 流）退化为设备级同步，调用方应传非零流。
     */
    public static native void _copyToHostBoolSync(long handle, int[] data, int wordCount, long streamHandle);

    /**
     * 分配为 host mapped pinned memory（zero-copy）。
     * <p>
     * GPU 经 device 视图写等价于写 host 内存，host 经 {@link #_readMappedBool} 直接读，零 D2H、零 sync。
     * 设计原则（真善美第 3 条）：把"behavior 是否写完"用 host 直接可见的 mapped 内存固化，
     * CPU 侧读取无需任何 CUDA 同步调用（不 flush WDDM 命令缓冲）。
     */
    public static native void _allocateBoolMapped(long handle, int size);

    /**
     * 从 mapped host 内存读取到 int[]（纯 host memcpy，零 CUDA 调用，不 flush WDDM）。
     * <p>
     * 调用方应先检查 generation 判断 GPU 已写完，再调此方法读完整 behavior，避免撕裂。
     */
    public static native void _readMappedBool(long handle, int[] data, int wordCount);

    public static native void _saveBool(long handle, String filename);

    public static native void _loadFromFileBool(long handle, String filename);

    public static native int _getSizeBool(long handle);

    public static native void _setRegionBool(long dstHandle, long destOffset, long srcHandle, long streamHandle);

    public static native void _copyRegionFromBool(long dstHandle, long dstOffset, long srcHandle, long srcOffset,
            long numBits, long streamHandle);

    public static native void _copyRegionFromHostBool(long handle, long destOffset, boolean[] srcData, int numBits, long streamHandle);

    // ====================================================================
    // Vector<int> 原生方法
    // ====================================================================
    public static native long _createVectorInt();

    public static native void _allocateInt(long handle, int size);

    public static native void _deleteVectorInt(long handle);

    public static native void _copyFromHostInt(long handle, int[] data, int count, long streamHandle);

    public static native void _copyToHostInt(long handle, int[] data, int count);

    public static native void _saveInt(long handle, String filename);

    public static native void _loadFromFileInt(long handle, String filename);

    public static native int _getSizeInt(long handle);

    public static native void _copyRegionFromInt(long dstHandle, long dstOffset, long srcHandle, long srcOffset,
            long count, long streamHandle);

    public static native void _setRegionInt(long dstHandle, long destOffset, long srcHandle, long streamHandle);

    public static native void _copyRegionFromHostInt(long dstHandle, long destOffset, int[] srcHostData, long count, long streamHandle);

    // ====================================================================
    // 向量算法原生方法
    // ====================================================================
    public static native void _scatterBits(long srcHandle, long dstHandle, long pHandle);

    public static native void _xorBool(long dstHandle, long srcHandle);

    public static native void _subtractBool(long aHandle, long bHandle, long bitOffset, long cHandle, long cIntOffset,
            long bitLength, long streamHandle);

    public static native void _multiplyByScalarInt(long handle, int scalar, long offset, long length, long streamHandle);

    /**
     * 用 PCG 哈希随机填充位向量（BNN 权重初始化）。同步：native 侧 launch 后 cudaStreamSynchronize(0)，
     * 保证返回时权重已写完。用于打破 BNN 零权重导致的零吸引子（零权重→零输出→零目标→零梯度→权重永不更新）。
     */
    public static native void _fillRandomBool(long handle, long seed);

    /**
     * 用 PCG 哈希随机填充整数向量，元素 ∈ [0, maxVal)（BNN 目标索引 P 初始化）。同步语义同上。
     */
    public static native void _fillRandomInt(long handle, int maxVal, long seed);

    // ====================================================================
    // Vector<float> 原生方法
    // 与 Vector<int> 对称：用于 CNN 浮点权重/激活/梯度。
    // ====================================================================
    public static native long _createVectorFloat();

    public static native void _allocateFloat(long handle, int size);

    public static native void _deleteVectorFloat(long handle);

    public static native void _copyFromHostFloat(long handle, float[] data, int count, long streamHandle);

    public static native void _copyToHostFloat(long handle, float[] data, int count);

    public static native void _saveFloat(long handle, String filename);

    public static native void _loadFromFileFloat(long handle, String filename);

    public static native int _getSizeFloat(long handle);

    public static native void _copyRegionFromFloat(long dstHandle, long dstOffset, long srcHandle, long srcOffset,
            long count, long streamHandle);

    public static native void _setRegionFloat(long dstHandle, long destOffset, long srcHandle, long streamHandle);

    public static native void _copyRegionFromHostFloat(long dstHandle, long destOffset, float[] srcHostData, long count,
            long streamHandle);

    public static native void _multiplyByScalarFloat(long handle, float scalar, long offset, long length,
            long streamHandle);

    /**
     * 用 PCG 哈希随机填充浮点向量，元素 ∈ [0, bound)（CNN 权重初始化）。同步语义同 _fillRandomInt。
     */
    public static native void _fillRandomFloat(long handle, float bound, long seed);

    /**
     * 分配为 host mapped pinned memory（zero-copy）。
     * <p>
     * GPU 经 device 视图写等价于写 host 内存，host 经 {@link #_readMappedFloat} 直接读，零 D2H、零 sync。
     * 设计原则（真善美第 3 条）：把"behavior 是否写完"用 host 直接可见的 mapped 内存固化，
     * CPU 侧读取无需任何 CUDA 同步调用（不 flush WDDM 命令缓冲）。
     */
    public static native void _allocateFloatMapped(long handle, int size);

    /**
     * 从 mapped host 内存读取到 float[]（纯 host memcpy，零 CUDA 调用，不 flush WDDM）。
     * <p>
     * 调用方应先检查 generation 判断 GPU 已写完，再调此方法读完整 behavior，避免撕裂。
     */
    public static native void _readMappedFloat(long handle, float[] data, int count);
}