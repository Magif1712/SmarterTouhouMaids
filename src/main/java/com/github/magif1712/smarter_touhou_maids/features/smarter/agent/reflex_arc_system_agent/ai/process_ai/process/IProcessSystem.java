package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.core.execution.event.Event;
import com.github.magif1712.smarter_touhou_maids.core.execution.MappedGenerationBuffer;

/**
 * 流程系统的顶层抽象边界（意识体契约）。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：意识域 C 中"流程系统"对外周呈现的本质模式只有——接收感觉、产出行为、
 *       自驱运转、可唤醒/关闭、可序列化、可诊断、有输入输出尺寸。C 中<b>没有</b>双环节律、
 *       三层子系统、anchor/inference/gradcell、span 语义、痕迹三缓冲等——那些是 urana 这个
 *       具体流程系统的实现，不进本接口。换流程系统（urana→别的）时，实现 IProcessSystem 即可，
 *       外周（SmarterClientService）运行期零改动。</li>
 *   <li><b>第3条</b>：把"可替换流程系统"这个不实在的约束，用实在的接口（有签名的方法）固化。
 *       与 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork}
 *       固化"可替换 NN"同构——nn 层与流程层各自有抽象边界，形成对称结构。</li>
 * </ul>
 * <p>
 * <b>与 INeuralNetwork 的分层</b>：本接口是流程系统对<b>外周</b>的契约（感觉/行为/启停）；
 * {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork}
 * 是 NN 对<b>流程系统</b>的契约（前向/反向/区域读写）。两层正交：换 nn 不影响本接口，
 * 换流程系统不影响 INeuralNetwork。UranaSystem 同时是这两层的消费者——它实现本接口对外服务，
 * 内部持 INeuralNetwork 做计算。
 * <p>
 * <b>节律参数归属</b>：双环节律参数（fastMinDt/slowMinDt）是 urana 特定的，<b>不</b>在本接口签名里
 * （由 UranaSystem 构造函数接收）。本接口的 {@link #setDtDebugEnabled} 只控制"是否打印 dt 日志"
 * 这个通用诊断开关，dt 的语义（哪几个环、间隔含义）由实现自管。
 *
 * @see com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.UranaSystem
 */
public interface IProcessSystem extends AutoCloseable {

    /**
     * 唤醒意识体：注入感觉缓冲、感觉就绪事件、行为通道，启动内部工作线程持续运转。
     * <p>
     * 节律参数（如 urana 的快/慢环 minDt）由实现的具体构造函数接收，不在本通用签名里——
     * 单环流程系统不需要 dt 参数，双环需要两个，签名不硬编码（真善美第2条：C 中"有节律"是模式，
     * 但"双环"不是，故 D 抽象层不固化双环）。
     *
     * @param feelingBuffer  外部持有的感觉缓冲区，由视觉采集写入，意识体每轮读取。
     * @param visionEvent    视觉采集完成事件，由外部创建并在视觉采集后 record。意识体跨流等待。非意识体所有。
     * @param behaviorChannel 行为产出通道，由外周创建注入。意识体只用其 producer 面。非意识体所有。
     */
    void awaken(VectorBase feelingBuffer, Event visionEvent, MappedGenerationBuffer behaviorChannel);

    /**
     * 关闭意识体，停止工作线程并释放所有资源。
     */
    void shutdown();

    /**
     * 将意识体核心网络序列化到磁盘。
     *
     * @param folderPath 目标文件夹路径。
     */
    void save(String folderPath);

    /**
     * dt 调试开关：开启时每轮输出轮间时间间隔到日志。关闭时零性能损失。
     * <p>
     * dt 的语义（几个环、间隔含义）由实现自管；本方法只控制通用诊断开关。
     */
    void setDtDebugEnabled(boolean enabled);

    /**
     * 意识体所需的感觉输入尺寸（外周据此创建 feelingBuffer）。
     */
    int feelingSize();

    /**
     * 意识体所需的行为输出尺寸（外周据此创建 MappedGenerationBuffer）。
     */
    int behaviorSize();
}
