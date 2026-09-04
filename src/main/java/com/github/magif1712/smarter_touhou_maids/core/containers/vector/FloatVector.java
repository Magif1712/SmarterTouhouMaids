package com.github.magif1712.smarter_touhou_maids.core.containers.vector;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import java.util.Objects;

/**
 * 浮点向量 (FloatVector)。
 * <p>
 * 完全镜像 {@link IntVector} 的结构（构造器、allocate、copyFromHost/copyToHost、save/loadFromFile、
 * copyFromFloatVector/copyRegionFrom、setRegion、copyRegionFromHost、multiplyByScalar、fillRandom、releaseResource），
 * 仅把整数载体换成 float 载体，标量参数用 float。
 * <p>
 * 所有 D2D/H2D 拷贝与计算 kernel 方法均显式接受 {@code long streamHandle}，
 * 由调用方指定异步操作所在的 CUDA 流（Urana 内部传 uranaStream，构造期初始化传 0L）。
 * <p>
 * 设计原则（真善美第3条）：把"CNN 权重/激活是浮点"这个不实在约束，用与 IntVector 对称的实在载体固化。
 */
public final class FloatVector extends VectorBase {
    public FloatVector() {
        super();
    }

    public FloatVector(int size) {
        super();
        allocate(size);
    }

    private FloatVector(long handle, int size) {
        super(handle, size);
    }

    @Override
    public void allocate(int size) {
        validateSize(size);
        long newHandle = VectorNative._createVectorFloat();
        VectorNative._allocateFloat(newHandle, size);
        setHandleAndSize(newHandle, size);
    }

    /**
     * 分配为 host mapped pinned memory（zero-copy）。
     * <p>
     * GPU 经 device 视图写等价于写 host 内存，host 经 {@link #readMappedToJava} 直接读。
     * 用于 CNN behavior 容器：主线程零 CUDA 调用读取，不 flush WDDM 命令缓冲。
     */
    public void allocateMapped(int size) {
        validateSize(size);
        long newHandle = VectorNative._createVectorFloat();
        VectorNative._allocateFloatMapped(newHandle, size);
        setHandleAndSize(newHandle, size);
    }

    /**
     * 静态工厂：创建一个 mapped FloatVector。
     */
    public static FloatVector mapped(int size) {
        FloatVector v = new FloatVector();
        v.allocateMapped(size);
        return v;
    }

    /**
     * 从 mapped host 内存读取浮点数据到 float[]（纯 host memcpy，零 CUDA 调用）。
     * <p>
     * 调用方应先检查 generation（{@code MappedCounter.getHostValue()}）判断 GPU 已写完，
     * 再调此方法读完整 buffer，避免撕裂。
     *
     * @param dstData 输出数组。
     * @param count   读取的元素数。
     */
    public void readMappedToJava(float[] dstData, int count) {
        Objects.requireNonNull(dstData);
        validateCount(count, dstData.length);
        validateCount(count, size());
        VectorNative._readMappedFloat(requireHandle(), dstData, count);
    }

    /**
     * 从 mapped host 内存读取全部数据到 float[]。
     */
    public void readMappedToJava(float[] dstData) {
        readMappedToJava(dstData, dstData.length);
    }

    public void copyFromHost(float[] data, int count, long streamHandle) {
        validateCount(count, data.length);
        validateCount(count, size());
        VectorNative._copyFromHostFloat(requireHandle(), data, count, streamHandle);
    }

    public void copyToHost(float[] data) {
        copyToHost(data, data.length);
    }

    public void copyToHost(float[] data, int count) {
        validateCount(count, data.length);
        validateCount(count, size());
        VectorNative._copyToHostFloat(requireHandle(), data, count);
    }

    public float[] toHostArray() {
        float[] data = new float[size()];
        copyToHost(data);
        return data;
    }

    @Override
    public void save(String path) {
        VectorNative._saveFloat(requireHandle(), Objects.requireNonNull(path));
    }

    public static FloatVector loadFromFile(String path) {
        Objects.requireNonNull(path);
        long h = VectorNative._createVectorFloat();
        if (h == 0)
            throw new RuntimeException("Failed to create native handle for loading.");

        VectorNative._loadFromFileFloat(h, path);
        int size = VectorNative._getSizeFloat(h);
        return new FloatVector(h, size);
    }

    public void copyFromFloatVector(Span destSpan, FloatVector source, Span srcSpan, long streamHandle) {
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

        VectorNative._copyRegionFromFloat(requireHandle(), destOffset, source.requireHandle(), srcOffset, count, streamHandle);
    }

    @Override
    public void copyRegionFrom(VectorBase source, Span srcSpan, Span destSpan, long streamHandle) {
        if (!(source instanceof FloatVector)) {
            throw new IllegalArgumentException("Source must be a FloatVector.");
        }
        this.copyFromFloatVector(destSpan, (FloatVector) source, srcSpan, streamHandle);
    }

    public void setRegion(Span destSpan, FloatVector source, long streamHandle) {
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

        VectorNative._setRegionFloat(requireHandle(), destOffset, source.requireHandle(), streamHandle);
    }

    /**
     * 把 host 端 boolean[] 装入本向量的指定区间（boolean → float 0/1 转换在内部完成）。
     * <p>
     * 设计原则第2条：伪代码 {@code buf_x.setRegion("<-", gSpan, G, stream)} 是鸭子类型——
     * setRegion 接收任意类型并在内部转换。Java 静态类型需显式重载，转换逻辑藏于此方法，
     * 使调用方（mapper.assembleX）能以统一 {@code setRegion(span, value, stream)} 模式调用。
     */
    public void setRegion(Span destSpan, boolean[] srcData, long streamHandle) {
        Objects.requireNonNull(destSpan);
        Objects.requireNonNull(srcData);

        int count = destSpan.getLength();
        if (count == 0) return;

        float[] floats = new float[count];
        int n = Math.min(srcData.length, count);
        for (int i = 0; i < n; i++) {
            floats[i] = srcData[i] ? 1.0f : 0.0f;
        }
        copyRegionFromHost(destSpan, floats, streamHandle);
    }

    /**
     * 把一个 long 标量装入本向量的指定区间（long → float 转换在内部完成）。
     * <p>
     * 设计原则第2条：同 {@link #setRegion(Span, boolean[], long)}，对应伪代码
     * {@code buf_x.setRegion("<-", dtSpan, dt, stream)} 的 long 参数情形。
     */
    public void setRegion(Span destSpan, long srcValue, long streamHandle) {
        Objects.requireNonNull(destSpan);

        int count = destSpan.getLength();
        if (count == 0) return;

        float[] floats = new float[count];
        floats[0] = (float) srcValue;
        copyRegionFromHost(destSpan, floats, streamHandle);
    }

    public void copyRegionFromHost(Span destSpan, float[] src_data, long streamHandle) {
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

        VectorNative._copyRegionFromHostFloat(requireHandle(), destOffset, src_data, count, streamHandle);
    }

    public void multiplyByScalar(float scalar, long streamHandle) {
        if (size() > 0) {
            multiplyByScalar(scalar, new Span(0, size()) {}, streamHandle);
        }
    }

    /**
     * 对向量的指定区间执行原地标量乘法。
     *
     * @param scalar       标量乘数（float）。
     * @param span         要操作的区间。
     * @param streamHandle CUDA 流句柄。
     */
    public void multiplyByScalar(float scalar, Span span, long streamHandle) {
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
        VectorNative._multiplyByScalarFloat(requireHandle(), scalar, offset, length, streamHandle);
    }

    /**
     * 用 PCG 哈希随机填充整个浮点向量，每个元素 ∈ [0, bound)。
     * <p>
     * 用于 CNN 权重初始化：权重需随机起步（与 BNN 同理——打破零吸引子）。
     * 同步语义（native 侧 launch 后 cudaStreamSynchronize(0)）：返回即写完。
     *
     * @param bound 上界（独占）；bound<=0 时填 0。
     * @param seed  64 位种子（不同向量应传不同子种子，避免同尺寸向量得到相同随机模式）。
     */
    public void fillRandom(float bound, long seed) {
        VectorNative._fillRandomFloat(requireHandle(), bound, seed);
    }

    @Override
    protected void releaseResource() {
        VectorNative._deleteVectorFloat(handle);
    }
}