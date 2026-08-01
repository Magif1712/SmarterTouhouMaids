package com.github.magif1712.smarter_touhou_maids.core.execution;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.execution.counter.MappedCounter;

/**
 * 映射代际缓冲区：mapped host 内存 buffer + generation 计数器的 producer-consumer 同步原语。
 * <p>
 * 纯组合两个 core 原语——{@link BoolVector#mapped(int)}（host mapped pinned memory，零拷贝）
 * 与 {@link MappedCounter}（host mapped uint32 计数器）——不引入任何新 native 调用。
 * <p>
 * 双面对象：
 * <ul>
 *   <li><b>producer 面</b>：{@link #getBuffer()} 返回 buffer 供写入，
 *       {@link #publish(long)} 在指定 stream 上递增 generation（应排在写 buffer 之后，流内有序）。</li>
 *   <li><b>consumer 面</b>：{@link #getGeneration()} 读当前 generation（纯 host 读，零 CUDA 调用），
 *       {@link #readTo(int[])} 从 mapped host 内存读到 int[]（纯 host memcpy）。</li>
 * </ul>
 * <p>
 * 同步语义：host 看到 generation 变化即意味着 producer 已在该 stream 上写完 buffer
 * （stream 内操作有序，increment 排在 buffer 写之后）。consumer 读 generation 检测变化后再 readTo，
 * 避免读到撕裂数据。
 * <p>
 * <b>纯技术原语，无领域语义</b>（真善美第1条"真"）：领域语义（如"行为通道"）由调用方在变量名承载，
 * 本类型只描述"mapped + generation + buffer"的技术组合。对称于 {@link BoolVector}——
 * 类型名纯技术，领域语义在变量名（如 {@code feelingBuffer} / {@code behaviorChannel}）。
 * <p>
 * <b>层级</b>（真善美第2条）：本类是组合原语，位于 {@link MappedCounter}（counter/ 子包）之上——
 * MappedCounter 是本类的下层实现之一，本类组合它而非替代它。放 execution 顶层，与 counter/event/stream
 * 子包同级，是 execution 层的组合原语；同时让 MappedCounter 不再孤立（两者同层配套，core/execution 内聚）。
 * <p>
 * 生命周期：由创建方（owner）负责 {@link #close()}。关闭须在 producer 停止写入之后
 * （如 producer 工作线程 join 完成后），确保无并发访问。
 */
public final class MappedGenerationBuffer implements AutoCloseable {

    private final BoolVector buffer;
    private final MappedCounter generation;

    /**
     * @param bits buffer 位长度。
     */
    public MappedGenerationBuffer(int bits) {
        this.buffer = BoolVector.mapped(bits);
        this.generation = new MappedCounter();
    }

    // === producer 面 ===

    /**
     * 返回 buffer 供 producer 写入。
     * <p>
     * buffer 为 host mapped pinned memory：GPU 经 device 视图写 = 写 host 内存（零拷贝）。
     */
    public BoolVector getBuffer() {
        return buffer;
    }

    /**
     * 发布本轮数据：在指定 stream 上递增 generation。
     * <p>
     * 应排在写 buffer 之后（流内有序），故 host 看到 generation 变化即意味着 buffer 已写完。
     *
     * @param streamHandle CUDA stream 句柄，generation.increment 在其上提交。
     */
    public void publish(long streamHandle) {
        generation.increment(streamHandle);
    }

    // === consumer 面 ===

    /**
     * 读取当前 generation 值（纯 host 读，零 CUDA 调用，不 flush WDDM）。
     * <p>
     * 值变化 = producer 已发布新数据且 buffer 已写完。
     */
    public int getGeneration() {
        return generation.getHostValue();
    }

    /**
     * 从 mapped host 内存读取完整 buffer 到 int[]（纯 host memcpy，零 CUDA 调用）。
     * <p>
     * 调用方应先判断 {@link #getGeneration()} 变化再调用，避免读到撕裂数据。
     *
     * @param bitPackedData 接收数据的 int[]（LSB-first bit 排布），长度须 >= buffer 位长 / 32。
     */
    public void readTo(int[] bitPackedData) {
        buffer.readMappedToJava(bitPackedData);
    }

    @Override
    public void close() {
        if (buffer != null) {
            buffer.close();
        }
        if (generation != null) {
            generation.close();
        }
    }
}
