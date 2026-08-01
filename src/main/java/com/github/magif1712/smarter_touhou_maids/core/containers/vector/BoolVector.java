package com.github.magif1712.smarter_touhou_maids.core.containers.vector;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import java.util.Objects;

/**
 * 布尔向量 (BoolVector)，底层使用位压缩存储。
 * <p>
 * 所有 D2D/H2D 拷贝方法均显式接受 {@code long streamHandle}，
 * 由调用方指定异步拷贝所在的 CUDA 流（Urana 内部传 uranaStream，构造期初始化传 0L）。
 */
public final class BoolVector extends VectorBase {
    public BoolVector() {
        super();
    }

    public BoolVector(int size) {
        super();
        allocate(size);
    }

    private BoolVector(long handle, int size) {
        super(handle, size);
    }

    @Override
    public void allocate(int size) {
        validateSize(size);
        long newHandle = VectorNative._createVectorBool();
        VectorNative._allocateBool(newHandle, size);
        setHandleAndSize(newHandle, size);
    }

    /**
     * 分配为 host mapped pinned memory（zero-copy）。
     * <p>
     * GPU 经 device 视图写等价于写 host 内存，host 经 {@link #readMappedToJava} 直接读。
     * 用于 MappedGenerationBuffer 的 behavior 容器：Urana 在 uranaStream 上写（D2D 到 mapped device 视图），
     * 主线程在 onClientTick 里零 CUDA 调用读取，不 flush WDDM 命令缓冲。
     * <p>
     * 设计原则（真善美第 3 条）：把"behavior 是否写完"用 host 直接可见的 mapped 内存固化，
     * 配合 {@code MappedCounter} 的 generation 判断 GPU 写完时机，CPU 侧读取全程零同步。
     *
     * @param size 位长度。
     */
    public void allocateMapped(int size) {
        validateSize(size);
        long newHandle = VectorNative._createVectorBool();
        VectorNative._allocateBoolMapped(newHandle, size);
        setHandleAndSize(newHandle, size);
    }

    /**
     * 静态工厂：创建一个 mapped BoolVector。
     */
    public static BoolVector mapped(int size) {
        BoolVector v = new BoolVector();
        v.allocateMapped(size);
        return v;
    }

    /**
     * 从 mapped host 内存读取位压缩数据到 int[]（纯 host memcpy，零 CUDA 调用）。
     * <p>
     * 调用方应先检查 generation（{@code MappedCounter.getHostValue()}）判断 GPU 已写完，
     * 再调此方法读完整 behavior，避免撕裂。{@code fresh=false} 时不应调用。
     *
     * @param bitPackedData 输出数组。
     * @param wordCount     读取的 word 数。
     */
    public void readMappedToJava(int[] bitPackedData, int wordCount) {
        Objects.requireNonNull(bitPackedData);
        validateCount(wordCount, bitPackedData.length);
        VectorNative._readMappedBool(requireHandle(), bitPackedData, wordCount);
    }

    /**
     * 从 mapped host 内存读取全部数据到 int[]。
     */
    public void readMappedToJava(int[] bitPackedData) {
        readMappedToJava(bitPackedData, bitPackedData.length);
    }



    public void copyFromHost(int[] bitPackedData, int wordCount, long streamHandle) {
        Objects.requireNonNull(bitPackedData);
        validateCount(wordCount, bitPackedData.length);
        VectorNative._copyFromHostBool(requireHandle(), bitPackedData, wordCount, streamHandle);
    }

    public void copyToHost(int[] bitPackedData) {
        copyToHost(bitPackedData, bitPackedData.length);
    }

    public void copyToHost(int[] bitPackedData, int wordCount) {
        Objects.requireNonNull(bitPackedData);
        validateCount(wordCount, bitPackedData.length);
        VectorNative._copyToHostBool(requireHandle(), bitPackedData, wordCount);
    }

    /**
     * 指定 stream 上的同步 D2H（cudaMemcpyAsync + cudaStreamSynchronize）。
     * <p>
     * 单流同步：只等 {@code streamHandle} 的先前工作，不 drain 其它流（如 GL 渲染流），
     * 故可在专用工作线程把 uranaStream 写入的 behavior 读出到 host，而不影响主线程渲染。
     * <b>阻塞调用线程</b>直到 D2H 完成（应在专用工作线程调用，勿在主渲染线程调用）。
     *
     * @param streamHandle 目标 CUDA 流句柄（应为非零流；0=NULL 流退化为设备级同步）。
     */
    public void copyToHost(int[] bitPackedData, int wordCount, long streamHandle) {
        Objects.requireNonNull(bitPackedData);
        validateCount(wordCount, bitPackedData.length);
        VectorNative._copyToHostBoolSync(requireHandle(), bitPackedData, wordCount, streamHandle);
    }

    @Override
    public void save(String path) {
        VectorNative._saveBool(requireHandle(), Objects.requireNonNull(path));
    }

    public static BoolVector loadFromFile(String path) {
        Objects.requireNonNull(path);
        long h = VectorNative._createVectorBool();
        if (h == 0) throw new RuntimeException("Failed to create native handle for loading.");

        VectorNative._loadFromFileBool(h, path);
        int size = VectorNative._getSizeBool(h);
        return new BoolVector(h, size);
    }

    public void copyFromBoolVector(Span destSpan, BoolVector source, Span srcSpan, long streamHandle) {
        Objects.requireNonNull(destSpan);
        Objects.requireNonNull(source);
        Objects.requireNonNull(srcSpan);
        long destOffset = destSpan.getOffset();
        long srcOffset = srcSpan.getOffset();
        long count = srcSpan.getLength();

        if (count == 0) return;
        if (destOffset + count > this.size()) throw new IllegalArgumentException("Destination out of bounds.");
        if (srcOffset + count > source.size()) throw new IllegalArgumentException("Source out of bounds.");

        VectorNative._copyRegionFromBool(requireHandle(), destOffset, source.requireHandle(), srcOffset, count, streamHandle);
    }

    @Override
    public void copyRegionFrom(VectorBase source, Span srcSpan, Span destSpan, long streamHandle) {
        if (!(source instanceof BoolVector)) {
            throw new IllegalArgumentException("Source must be a BoolVector.");
        }
        this.copyFromBoolVector(destSpan, (BoolVector) source, srcSpan, streamHandle);
    }

    public void setRegion(Span destSpan, BoolVector source, long streamHandle) {
        Objects.requireNonNull(destSpan);
        Objects.requireNonNull(source);
        if (source.size() == 0) return;

        long destOffset = destSpan.getOffset();
        long count = source.size();

        if (destSpan.getLength() != count) {
            throw new IllegalArgumentException(String.format(
                "Destination span length (%d) must match source vector size (%d).",
                destSpan.getLength(), count));
        }
        if (destOffset + count > this.size()) throw new IllegalArgumentException("Destination out of bounds.");

        VectorNative._setRegionBool(requireHandle(), destOffset, source.requireHandle(), streamHandle);
    }

    public void copyRegionFromHost(Span destSpan, boolean[] src_data, long streamHandle) {
        Objects.requireNonNull(destSpan);
        Objects.requireNonNull(src_data);

        int num_bits = destSpan.getLength();
        if (num_bits == 0) return;

        long destOffset = destSpan.getOffset();
        if (destOffset + num_bits > this.size()) {
            throw new IllegalArgumentException("Destination span is out of bounds for this vector.");
        }
        if (num_bits > src_data.length) {
            throw new IllegalArgumentException("Source data array is too small for the destination span.");
        }

        VectorNative._copyRegionFromHostBool(requireHandle(), destOffset, src_data, num_bits, streamHandle);
    }

    /**
     * 用 PCG 哈希随机填充整个位向量（每个 32-bit word 一个随机值 = 32 个随机 bit）。
     * <p>
     * 用于 BNN 权重初始化。同步语义（native 侧 launch 后 cudaStreamSynchronize(0)）：
     * 调用返回时随机数据已写完，保证后续在任意流上的 forward 读到完整权重。
     * <p>
     * 设计原则（真善美第 3 条）：把"权重需要随机起步"这个不实在的要求，
     * 用实在的 GPU PCG kernel + 同步固化成"返回即写完"的确定性语义。
     *
     * @param seed 64 位种子（不同向量应传不同子种子，避免同尺寸向量得到相同随机模式）。
     */
    public void fillRandom(long seed) {
        VectorNative._fillRandomBool(requireHandle(), seed);
    }

    @Override
    protected void releaseResource() {
        VectorNative._deleteVectorBool(handle);
    }
}
