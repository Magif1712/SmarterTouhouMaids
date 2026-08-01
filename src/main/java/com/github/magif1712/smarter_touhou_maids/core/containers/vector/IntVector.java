package com.github.magif1712.smarter_touhou_maids.core.containers.vector;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import java.util.Objects;

/**
 * 整数向量 (IntVector)。
 * <p>
 * 所有 D2D/H2D 拷贝与计算 kernel 方法均显式接受 {@code long streamHandle}，
 * 由调用方指定异步操作所在的 CUDA 流（Urana 内部传 uranaStream，构造期初始化传 0L）。
 */
public final class IntVector extends VectorBase {
    public IntVector() {
        super();
    }

    public IntVector(int size) {
        super();
        allocate(size);
    }

    private IntVector(long handle, int size) {
        super(handle, size);
    }

    @Override
    public void allocate(int size) {
        validateSize(size);
        long newHandle = VectorNative._createVectorInt();
        VectorNative._allocateInt(newHandle, size);
        setHandleAndSize(newHandle, size);
    }

    public void copyFromHost(int[] data, int count, long streamHandle) {
        validateCount(count, data.length);
        validateCount(count, size());
        VectorNative._copyFromHostInt(requireHandle(), data, count, streamHandle);
    }

    public void copyToHost(int[] data) {
        copyToHost(data, data.length);
    }

    public void copyToHost(int[] data, int count) {
        validateCount(count, data.length);
        validateCount(count, size());
        VectorNative._copyToHostInt(requireHandle(), data, count);
    }

    public int[] toHostArray() {
        int[] data = new int[size()];
        copyToHost(data);
        return data;
    }

    @Override
    public void save(String path) {
        VectorNative._saveInt(requireHandle(), Objects.requireNonNull(path));
    }

    public static IntVector loadFromFile(String path) {
        Objects.requireNonNull(path);
        long h = VectorNative._createVectorInt();
        if (h == 0)
            throw new RuntimeException("Failed to create native handle for loading.");

        VectorNative._loadFromFileInt(h, path);
        int size = VectorNative._getSizeInt(h);
        return new IntVector(h, size);
    }

    public void copyFromIntVector(Span destSpan, IntVector source, Span srcSpan, long streamHandle) {
        Objects.requireNonNull(destSpan);
        Objects.requireNonNull(source);
        Objects.requireNonNull(srcSpan);
        long destOffset = destSpan.getOffset();
        long srcOffset = srcSpan.getOffset();
        long count = srcSpan.getLength();

        if (count == 0)
            return;
        if (destOffset + count > this.size())
            throw new IllegalArgumentException("Destination out of bounds.");
        if (srcOffset + count > source.size())
            throw new IllegalArgumentException("Source out of bounds.");

        VectorNative._copyRegionFromInt(requireHandle(), destOffset, source.requireHandle(), srcOffset, count, streamHandle);
    }

    @Override
    public void copyRegionFrom(VectorBase source, Span srcSpan, Span destSpan, long streamHandle) {
        if (!(source instanceof IntVector)) {
            throw new IllegalArgumentException("Source must be an IntVector.");
        }
        this.copyFromIntVector(destSpan, (IntVector) source, srcSpan, streamHandle);
    }

    public void setRegion(Span destSpan, IntVector source, long streamHandle) {
        Objects.requireNonNull(destSpan);
        Objects.requireNonNull(source);
        if (source.size() == 0)
            return;

        long destOffset = destSpan.getOffset();
        long count = source.size();

        if (destSpan.getLength() != count) {
            throw new IllegalArgumentException(String.format(
                    "Destination span length (%d) must match source vector size (%d).",
                    destSpan.getLength(), count));
        }
        if (destOffset + count > this.size())
            throw new IllegalArgumentException("Destination out of bounds.");

        VectorNative._setRegionInt(requireHandle(), destOffset, source.requireHandle(), streamHandle);
    }

    public void copyRegionFromHost(Span destSpan, int[] src_data, long streamHandle) {
        Objects.requireNonNull(destSpan);
        Objects.requireNonNull(src_data);

        int count = destSpan.getLength();
        if (count == 0) return;

        long destOffset = destSpan.getOffset();
        if (destOffset + count > this.size()) {
            throw new IllegalArgumentException("Destination span is out of bounds for this vector.");
        }
        if (count > src_data.length) {
            throw new IllegalArgumentException("Source data array is too small for the destination span.");
        }

        // 调用底层的、保持不变的 native 接口
        VectorNative._copyRegionFromHostInt(requireHandle(), destOffset, src_data, count, streamHandle);
    }

    public void multiplyByScalar(int scalar, long streamHandle) {
        if (size() > 0) {
            multiplyByScalar(scalar, new Span(0, size()) {}, streamHandle);
        }
    }

    /**
     * 对向量的指定区间执行原地标量乘法。
     *
     * @param scalar       标量乘数。
     * @param span         要操作的区间。
     * @param streamHandle CUDA 流句柄。
     */
    public void multiplyByScalar(int scalar, Span span, long streamHandle) {
        Objects.requireNonNull(span);
        long offset = span.getOffset();
        long length = span.getLength();

        if (length <= 0) {
            return;
        }
        if (offset < 0 || length < 0 || offset + length > size()) {
            throw new IllegalArgumentException(String.format(
                    "The specified span [offset=%d, length=%d] is out of bounds for vector of size %d.",
                    offset, length, size()));
        }
        VectorNative._multiplyByScalarInt(requireHandle(), scalar, offset, length, streamHandle);
    }

    /**
     * 用 PCG 哈希随机填充整个整数向量，每个元素 ∈ [0, maxVal)。
     * <p>
     * 用于 BNN 目标索引 P 的初始化：P 非置换、是随机散射目标，随机化后输入 bit 均匀散射到输出空间，
     * 避免全零 P 把所有输入挤到 bit 0（见 inference_ops.cu 的 bnn_push_p）。
     * 同步语义（native 侧 launch 后 cudaStreamSynchronize(0)）：返回即写完。
     *
     * @param maxVal  上界（独占）；maxVal<=0 时填 0。
     * @param seed    64 位种子。
     */
    public void fillRandom(int maxVal, long seed) {
        VectorNative._fillRandomInt(requireHandle(), maxVal, seed);
    }

    @Override
    protected void releaseResource() {
        VectorNative._deleteVectorInt(handle);
    }
}
