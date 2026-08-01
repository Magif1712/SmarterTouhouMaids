package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.core.execution.event.Event;
import com.github.magif1712.smarter_touhou_maids.core.execution.MappedGenerationBuffer;

/**
 * AI 系统的顶层抽象边界（外周契约）。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：意识域 C 中"ai"是一个类型概念——AI 有多种流派（符号主义逻辑推理、
 *       传统机器学习统计分类、连接主义神经网络），它们既不都依赖神经网络，也不都依赖显式流程。
 *       "nn + process 组合"只是连接主义路线下的一种 ai 实现，不是 ai 的全部。C 中有
 *       "ai 是可替换类型概念"的模式，故 D 中应有 ai 接口。外周（SmarterClientService）
 *       只依赖本接口，换 ai 实现时外周零改动。</li>
 *   <li><b>第3条</b>：把"可替换 ai"这个不实在的约束，用实在的接口（有签名的方法）固化。
 *       附属模组作者实现本接口即可提供不同范式的 ai（如纯规则 ai、统计分类 ai），
 *       不需碰 nn/process。发布前固化此公共契约，避免后续 Mixin 附属模组的兼容性灾难——
 *       一旦发布且有人用 Mixin 扩展，再重构接口将极难回收。</li>
 * </ul>
 * <p>
 * <b>与 IProcessSystem 的分层</b>：本接口是 ai 对<b>外周</b>的顶层契约（所有流派 ai 都实现）；
 * {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.IProcessSystem}
 * 是流程系统对<b>process_ai 型 ai 实现类</b>的契约（只有 nn+process 型 ai 内部用）。
 * 两者签名相似但语义层次不同：纯规则 ai 直接实现本接口，不涉及 IProcessSystem。
 * <p>
 * <b>边界固定</b>：feelingBuffer（视觉位向量）、MappedGenerationBuffer（行为通道）是外周与 ai 的固定边界，
 * 所有流派的 ai 都接受此格式。纯规则 ai 若需结构化信息，自行从位向量转换（ai 实现的内部事务）。
 *
 * @see com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.ProcessAiSystem
 */
public interface IAiSystem extends AutoCloseable {

    /**
     * 唤醒 ai：注入感觉缓冲、感觉就绪事件、行为通道，启动内部运转。
     *
     * @param feelingBuffer  外部持有的感觉缓冲区，由视觉采集写入，ai 每轮读取。
     * @param visionEvent    视觉采集完成事件，由外部创建并在视觉采集后 record。ai 跨流等待。非 ai 所有。
     * @param behaviorChannel 行为产出通道，由外周创建注入。ai 只用其 producer 面。非 ai 所有。
     */
    void awaken(VectorBase feelingBuffer, Event visionEvent, MappedGenerationBuffer behaviorChannel);

    /**
     * 关闭 ai，停止运转并释放所有资源。
     */
    void shutdown();

    /**
     * 将 ai 核心状态序列化到磁盘。
     *
     * @param folderPath 目标文件夹路径。
     */
    void save(String folderPath);

    /**
     * dt 调试开关：开启时输出轮间时间间隔到日志。关闭时零性能损失。
     * <p>
     * dt 的语义（几个环、间隔含义）由实现自管；本方法只控制通用诊断开关。
     */
    void setDtDebugEnabled(boolean enabled);

    /**
     * ai 所需的感觉输入尺寸（外周据此创建 feelingBuffer）。
     */
    int feelingSize();

    /**
     * ai 所需的行为输出尺寸（外周据此创建 MappedGenerationBuffer）。
     */
    int behaviorSize();
}
