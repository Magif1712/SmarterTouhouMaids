package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.bnn_mapper.nn.standard_bnn;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.AbstractBnnNeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.containers.BnnNetworkData;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.standard_bnn.ElasticOpsNative;

/**
 * 标准二值神经网络——惊跳反射（startle reflex）：带输入变化门控重连的 bnn（叶子，新版架构）。
 * <p>
 * 在家族内核 {@link AbstractBnnNeuralNetwork} 的基础上，每次 {@link #forward} 前检测输入变化：
 * 当某个输入位发生变化且对应的 q 通道断连（q[i]==0）时，精确重连该通道——
 * <ul>
 *   <li>静止期：输入不变 → 不触发重连 → 安静睡眠（合理）；</li>
 *   <li>环境变化：输入变化 → 精确重连断连通道 → 信号传入 → 苏醒。</li>
 * </ul>
 * 解决「q 饱和到 0 时输入通道被物理断开、AI 睡眠后醒不过来」：相比弹性恢复（随机翻转，
 * 可能翻不到关键位）与权重衰减（持续拉向 0.5、阻止学习），门控重连是<b>精确恢复信号通路</b>。
 * <p>
 * 与旧版（{@code urana_process_original.nn.bnn.standard_bnn.StandardBnnNeuralNetwork}，
 * 实现旧版 INeuralNetwork）的关系：本类继承<b>新版</b>家族内核（实现新版 INeuralNetwork），
 * 门控重连算子直接复用旧树 {@link ElasticOpsNative#_reconnectOnInputChange}
 * （native 符号绑定旧树类名，零新增 native）。本类与 original_bnn 叶子
 * （{@code ...bnn_mapper.nn.original_bnn.BnnNeuralNetwork}）共用同一 BNN mechanics 与
 * 同一权重文件（b_original.bin）⇒ 在两种 nn 之间切换不丢已学权重。
 * <p>
 * {@link #forward} 是新版接口的<b>唯一前向入口</b>（内部按 fwTraceForBw 是否为 null 分走
 * forwardNoFz（快环推理）/ forwardStoreFz（慢环训练））——在入口先重连 ⇒ 推理与训练两路
 * 都生效，与旧版分别覆写 forward/forwardForTraining 语义等价。
 * <p>
 * {@code prevInput} 是自持状态、<b>不落盘</b>（与旧版一致）：重启后首次 forward 视其为全零
 * ⇒ 活跃的断连位会被全量重连一次；线程模型与旧版相同（只在 forward 内、同一 stream 上顺序访问）。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：门控重连是本实现层的模式，藏于本包；urana 经新版 INeuralNetwork 访问，不感知其存在。</li>
 *   <li><b>第3条</b>：与 bnn（original_bnn）并列于 bnn_mapper 的专属 NN 插槽，任一可被删换而不改上层 urana。</li>
 *   <li><b>第4条</b>：把「醒不过来」这个不实在的问题，实在化为 {@link ElasticOpsNative#_reconnectOnInputChange} 操作。</li>
 * </ul>
 * <p>
 * 原则6 字形：{@code reconnectOnInputChange} 的接收者被写（prevInput 跨调用保持 + q 原地修改，
 * 都会被后续 forward 观察到）⇒ 接收者是出参 ⇒ {@code <-}、标记落首部；
 * {@code forward} 与接口同字形 {@code ->}（覆写不新增分歧）。
 */
public class StandardBnnNeuralNetwork extends AbstractBnnNeuralNetwork {

    /** 上一步输入（自持状态，不落盘）：门控重连的「变化」基准。初始全零 ⇒ 首次 forward 把所有活跃断连位重连一次。 */
    private final BoolVector prevInput;

    /**
     * 新建带门控重连的 bnn 网络（随机初始化权重）。
     *
     * @param inputSize  输入向量尺寸（由 urana 组装链传入）。
     * @param outputSize 输出向量尺寸。
     */
    public StandardBnnNeuralNetwork(int inputSize, int outputSize) {
        super(inputSize, outputSize);
        this.prevInput = new BoolVector(inputSize);
    }

    /**
     * 从已加载权重构造（供 {@link #loadFromFile} 复用）。
     */
    private StandardBnnNeuralNetwork(BnnNetworkData networkData, int inputSize, int outputSize) {
        super(networkData, inputSize, outputSize);
        this.prevInput = new BoolVector(inputSize);
    }

    /**
     * 从磁盘加载 BNN 权重，自动反推尺寸（权重文件与 bnn 同款：b_original.bin）。
     *
     * @param folderPath 权重目录。
     * @return 加载完成的实例。
     */
    public static StandardBnnNeuralNetwork loadFromFile(String folderPath) {
        BnnNetworkData net = loadNetworkData(folderPath);
        int inputSize = net.getHyperparameters().getSizeA0();
        int outputSize = net.getHyperparameters().getSizeA1();
        return new StandardBnnNeuralNetwork(net, inputSize, outputSize);
    }

    // ==================== 前向（注入门控重连） ====================

    /**
     * 新版唯一前向入口：先做输入变化门控重连（惊跳反射），再走家族内核前向。
     * fwTraceForBw 非 null（慢环训练，forwardStoreFz）与为 null（快环推理，forwardNoFz）两路都先重连，
     * 与旧版分别覆写 forward/forwardForTraining 语义等价。
     */
    @Override
    public void forward(VectorBase x, long stream /* -> */, VectorBase y, Object fwTraceForBw) {
        reconnectOnInputChange(/* <- */ stream);
        super.forward(x, stream /* -> */, y, fwTraceForBw);
    }

    /**
     * 输入变化门控重连（惊跳反射）。
     * <p>
     * 对每个位 i：changed[i] = currentInput[i] XOR prevInput[i]；
     * 若 changed[i] 且 q[i]==0，则 q[i]:=1；随后 prevInput 更新为当前输入副本（native 内完成）。
     * <p>
     * 原则6：接收者被写（prevInput 跨调用保持 + q 原地修改，都会被后续 forward 观察到）
     * ⇒ 接收者是出参 ⇒ {@code <-}、标记落首部。
     */
    private void reconnectOnInputChange(/* <- */ long stream) {
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
