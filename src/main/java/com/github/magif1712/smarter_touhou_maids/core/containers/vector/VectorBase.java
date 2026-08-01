package com.github.magif1712.smarter_touhou_maids.core.containers.vector;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;

/**
 * 向量的抽象基类，负责句柄生命周期管理。
 * <p>
 * 设计原则（真善美第1条"真"）：原 IVector 接口与 VectorBase 能力完全重叠，是多余抽象——
 * 读懂 VectorBase 即懂 vector，多一层接口降低可读性。删除 IVector，VectorBase 独立承载抽象。
 */
public abstract class VectorBase implements AutoCloseable {
    protected long handle;
    protected int size;
    protected boolean ownsHandle;

    protected VectorBase() {
        this.handle = 0L;
        this.size = 0;
        this.ownsHandle = false;
    }

    protected VectorBase(long handle, int size) {
        if (handle == 0L) {
            throw new IllegalStateException("Native vector creation failed");
        }
        this.handle = handle;
        this.size = size;
        this.ownsHandle = true;
    }

    protected void setHandleAndSize(long handle, int size) {
        if (handle == 0L) {
            throw new IllegalStateException("Native vector creation failed");
        }
        close();
        this.handle = handle;
        this.size = size;
        this.ownsHandle = true;
    }

    public final int size() {
        return size;
    }

    public final long handle() {
        return handle;
    }

    public final boolean isInitialized() {
        return ownsHandle && handle != 0L;
    }

    public final long requireHandle() {
        if (!isInitialized()) {
            throw new IllegalStateException("Vector has already been released or was not initialized");
        }
        return handle;
    }

    @Override
    public final void close() {
        if (this.ownsHandle) {
            releaseResource();
            this.ownsHandle = false;
            this.handle = 0L;
            this.size = 0;
        }
    }

    /**
     * 从另一个向量对象移动资源所有权到当前对象。
     * 实现移动语义，确保显存资源的唯一所有权。
     */
    public void moveFrom(VectorBase other) {
        if (this == other) {
            throw new IllegalArgumentException("Cannot move an object to itself.");
        }
        close();
        this.handle = other.handle;
        this.size = other.size;
        this.ownsHandle = other.ownsHandle;

        other.handle = 0L;
        other.size = 0;
        other.ownsHandle = false;
    }

    protected abstract void releaseResource();

    /**
     * 从源向量的指定区间拷贝数据到本向量的指定区间（D2D 拷贝）。
     *
     * @param source       源向量。
     * @param srcSpan      源区间。
     * @param destSpan     目标区间。
     * @param streamHandle CUDA 流句柄（显式控制异步拷贝所在的流）。
     */
    public abstract void copyRegionFrom(VectorBase source, Span srcSpan, Span destSpan, long streamHandle);

    /**
     * 为向量分配指定大小的显存空间。
     */
    public abstract void allocate(int size);

    /**
     * 将向量序列化到磁盘。
     */
    public abstract void save(String path);

    // 内部校验工具
    protected static void validateSize(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
    }

    protected static void validateCount(int count, int length) {
        if (count < 0 || count > length) {
            throw new IllegalArgumentException("count is out of range");
        }
    }
}