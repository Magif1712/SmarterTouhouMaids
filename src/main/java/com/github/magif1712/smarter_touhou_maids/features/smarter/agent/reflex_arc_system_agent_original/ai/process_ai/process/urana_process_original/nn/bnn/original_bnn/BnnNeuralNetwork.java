package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.original_bnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.AbstractBnnNeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.BnnNetworkData;

/**
 * 「一开始」的 bnn：朴素二值神经网络，无额外行为。
 * <p>
 * 全部 INeuralNetwork 行为继承自家族内核 {@link AbstractBnnNeuralNetwork}（朴素 forward），
 * 本类不 override 任何方法。作为历史/基线叶子保留，与 standard_bnn 并列于 {@code nn.bnn} 家族下；
 * 二者互不依赖，任一可删（真善美第3条：哪怕删除原模式、用新模式替换也不改上层代码）。
 * <p>
 * 包名 {@code original_bnn} 编码「一开始」语义；registry id 保持稳定字符串 {@code "bnn"}
 * （存档 / lang key / GUI 依赖，不可改）。类名沿用 {@code Bnn*} 前缀。
 * <p>
 * 设计原则（真善美第3条）：本类是 nn 的一个可替换模式（Y1=original_bnn），与 standard_bnn（Y2）并列；
 * 切换或删除任一都不改上层 urana 代码。它经 {@link BnnRegistration} 以附属模组方式自注册，
 * 不在 AiModeDefaults 里登记（AiModeDefaults 只管核心默认 standard_bnn）。
 */
public class BnnNeuralNetwork extends AbstractBnnNeuralNetwork {

    /**
     * 新建 bnn 网络（随机初始化权重）。
     */
    public BnnNeuralNetwork(int inputSize, int outputSize) {
        super(inputSize, outputSize);
    }

    private BnnNeuralNetwork(BnnNetworkData networkData, int inputSize, int outputSize) {
        super(networkData, inputSize, outputSize);
    }

    /**
     * 从磁盘加载 bnn 权重，自动反推尺寸。
     */
    public static BnnNeuralNetwork loadFromFile(String folderPath) {
        BnnNetworkData net = loadNetworkData(folderPath);
        int in = net.getHyperparameters().getSizeA0();
        int out = net.getHyperparameters().getSizeA1();
        return new BnnNeuralNetwork(net, in, out);
    }
}
