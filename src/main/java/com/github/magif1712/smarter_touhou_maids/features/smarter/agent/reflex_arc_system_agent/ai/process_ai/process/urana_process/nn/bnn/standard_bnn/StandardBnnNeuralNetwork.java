package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.standard_bnn;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.AbstractBnnNeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.BnnNetworkData;

/**
 * 标准 nn：带输入变化门控重连的 bnn（惊跳反射）。
 * <p>
 * 在家族内核 {@link AbstractBnnNeuralNetwork} 的基础上，每次 {@link #forward} 前检测输入变化，
 * 当某个输入位发生变化且对应的 q 通道断连时，精确重连该通道，确保环境变化时 AI 能够"苏醒"。
 * <p>
 * 核心机制——惊跳反射（startle reflex）：
 * <ul>
 *   <li>静止期：输入不变 → 不触发重连 → AI 安静睡眠（合理）</li>
 *   <li>环境变化：输入变化 → 精确重连断连通道 → 信号传入 → AI 苏醒</li>
 * </ul>
 * 这解决了"AI 睡眠后醒不过来"的核心问题：q 饱和到 0 时输入通道被物理断开，
 * 环境变化信号无法传入网络。门控重连在输入变化时精确恢复信号通路，
 * 不像弹性恢复那样随机翻转（可能翻不到关键位），也不像权重衰减那样持续拉向 0.5（阻止学习）。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：门控重连是 standard_bnn 实现层的模式，藏在 standard_bnn 包里；urana 通过
 *       {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork INeuralNetwork}
 *       接口访问，不知道门控重连的存在。</li>
 *   <li><b>第3条</b>：standard_bnn 与 original_bnn 并列于 nn.bnn 家族下，二者都是 nn 的可替换实现，
 *       互不依赖，换/删任一都不改上层 urana 代码。本类是核心默认 nn（经 AiModeDefaults 注册）。</li>
 *   <li><b>第4条</b>：把"醒不过来"这个不实在的问题，实在化为
 *       {@link ElasticOpsNative#_reconnectOnInputChange} 操作——输入变化时精确恢复信号通路。</li>
 * </ul>
 */
public class StandardBnnNeuralNetwork extends AbstractBnnNeuralNetwork {

    private final BoolVector prevInput;

    /**
     * 新建带门控重连的 bnn 网络（随机初始化权重）。
     *
     * @param inputSize  输入向量尺寸
     * @param outputSize 输出向量尺寸
     */
    public StandardBnnNeuralNetwork(int inputSize, int outputSize) {
        super(inputSize, outputSize);
        this.prevInput = new BoolVector(inputSize);
    }

    private StandardBnnNeuralNetwork(BnnNetworkData networkData, int inputSize, int outputSize) {
        super(networkData, inputSize, outputSize);
        this.prevInput = new BoolVector(inputSize);
    }

    /**
     * 从磁盘加载 bnn 权重，自动反推尺寸。
     */
    public static StandardBnnNeuralNetwork loadFromFile(String folderPath) {
        BnnNetworkData net = loadNetworkData(folderPath);
        int in = net.getHyperparameters().getSizeA0();
        int out = net.getHyperparameters().getSizeA1();
        return new StandardBnnNeuralNetwork(net, in, out);
    }

    // ==================== 前向（注入门控重连） ====================

    @Override
    public void forward(long stream) {
        reconnectOnInputChange(stream);
        super.forward(stream);
    }

    @Override
    public void forwardForTraining(VectorBase fz, long stream) {
        reconnectOnInputChange(stream);
        super.forwardForTraining(fz, stream);
    }

    /**
     * 输入变化门控重连（惊跳反射）。
     * <p>
     * 在每次 forward 之前调用。检测当前输入与上一步输入的差异，
     * 当某个输入位发生变化且对应的 q 通道断连（q[i]==0）时，精确重连该通道。
     * <p>
     * 静止期：输入不变 → 不触发重连 → AI 安静睡眠（合理）
     * 环境变化：输入变化 → 精确重连 → 信号传入 → AI 苏醒
     * <p>
     * prevInput 初始为全零，因此第一次 forward 时所有活跃输入位对应的断连通道都会被重连。
     */
    private void reconnectOnInputChange(long stream) {
        ElasticOpsNative._reconnectOnInputChange(
                io.getInput().getVector().requireHandle(),
                prevInput.requireHandle(),
                networkData.getHyperparameters().getQ().requireHandle(),
                stream);
    }

    // ==================== 生命周期 ====================

    @Override
    public void close() throws Exception {
        if (prevInput != null) prevInput.close();
        super.close();
    }
}
