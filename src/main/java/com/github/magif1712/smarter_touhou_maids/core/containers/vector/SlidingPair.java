package com.github.magif1712.smarter_touhou_maids.core.containers.vector;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import java.io.Closeable;

/**
 * 管理一对在时间上滑动的向量，一个代表“活动”状态，一个代表“历史”状态。
 * <p>
 * 这个容器专门用于高效处理时间序列数据，例如，在AI决策中同时需要当前状态和前一刻的状态。
 * 它通过角色转换（而非数据复制）来实现时间窗口的滑动。
 *
 * @param <T> 向量的具体类型，必须是 {@link VectorBase} 的子类。
 */
public class SlidingPair<T extends VectorBase> implements Closeable {

    private T precipitate; // 沉淀物，代表稳定、已固化的状态
    private T suspension; // 悬浮物，代表活跃、正在计算的状态

    /**
     * 构造一个滑动对。
     *
     * @param precipitateSlot 初始时扮演“沉淀物”角色的向量。
     * @param suspensionSlot  初始时扮演“悬浮物”角色的向量。
     */
    public SlidingPair(T precipitateSlot, T suspensionSlot) {
        this.precipitate = precipitateSlot;
        this.suspension = suspensionSlot;
    }

    /**
     * 获取“沉淀物”状态的向量。
     * <p>
     * 在 {@link #slide()} 操作之后，这个向量将持有之前“悬浮物”状态的数据。
     *
     * @return 代表沉淀物状态的向量。
     */
    public T getPrecipitate() {
        return precipitate;
    }

    /**
     * 获取“悬浮物”状态的向量。
     * <p>
     * 这通常是最新、最相关的数据，是算法读取和处理的主要目标。
     *
     * @return 代表悬浮物状态的向量。
     */
    public T getSuspension() {
        return suspension;
    }

    /**
     * 向前滑动一个时间步，进行角色转换（沉淀过程）。
     * <p>
     * 这个操作会将 {@code suspension} 的角色赋予给 {@code precipitate}，
     * 同时将原 {@code precipitate} 的角色赋予给 {@code suspension}。
     * 这是一个高效的指针交换操作。
     * <p>
     * <b>重要:</b> 调用此方法后，{@code suspension} 向量的状态将变为之前 {@code precipitate} 的状态，
     * 它现在是一个“空的”或“陈旧的”容器，可以安全地被下一个时间步的新数据覆写。
     */
    public void slide() {
        // java对于能"."出来的对象，才可以进行交换引用
        T temp = precipitate;
        precipitate = suspension;
        suspension = temp;
    }

    public enum Target {
        SUSPENSION, // 悬浮物
        PRECIPITATE // 沉淀物
    }

    /**
     * [底层API] 根据指定的 target，通过移动语义更新容器中的向量。
     * @param target 操作的目标，SUSPENSION 或 PRECIPITATE。
     * @param source 新的向量数据，其所有权将被转移。
     */
    public void move(Target target, T source) {
        T destination = (target == Target.SUSPENSION) ? this.suspension : this.precipitate;

        if (source.size() != destination.size()) {
            throw new IllegalArgumentException("Size mismatch for move operation.");
        }

        destination.moveFrom(source);
    }

    /**
     * [底层API] 根据指定的 target，从源向量复制一个区域到目标向量（D2D 拷贝）。
     * @param target       操作的目标，SUSPENSION 或 PRECIPITATE。
     * @param source       源向量
     * @param srcSpan      源区间
     * @param destSpan     目标区间
     * @param streamHandle CUDA 流句柄。
     */
    public void copyRegionTo(Target target, T source, Span srcSpan, Span destSpan, long streamHandle) {
        T destination = (target == Target.SUSPENSION) ? this.suspension : this.precipitate;
        destination.copyRegionFrom(source, srcSpan, destSpan, streamHandle);
    }

    /**
     * 路径一：区间覆盖（写入语义）。
     * 从 source 的指定区间拷贝数据到当前 suspension，source 所有权不变。
     *
     * @param streamHandle CUDA 流句柄。
     */
    public void push(T source, Span srcSpan, Span destSpan, long streamHandle) {
        copyRegionTo(Target.SUSPENSION, source, srcSpan, destSpan, streamHandle);
    }

    /**
     * 路径二：引用交换（转移语义）。
     * 直接用 source 替换 suspension。source 的所有权转移给本容器，
     * 调用者此后不得再访问、修改或关闭该向量。
     *
     * @throws IllegalArgumentException 如果 source 尺寸与 suspension 不匹配
     */
    public void push(T source) {
        move(Target.SUSPENSION, source);
    }

    @Override
    public void close() {
        if (precipitate != null) {
            precipitate.close();
        }
        // 防止 precipitate 和 suspension 指向同一对象时 double-free
        if (suspension != null && suspension != precipitate) {
            suspension.close();
        }
    }
}