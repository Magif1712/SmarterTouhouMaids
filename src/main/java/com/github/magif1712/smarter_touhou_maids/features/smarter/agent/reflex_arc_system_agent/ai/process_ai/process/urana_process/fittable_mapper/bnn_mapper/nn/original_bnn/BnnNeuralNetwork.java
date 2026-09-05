package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.bnn_mapper.nn.original_bnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.bnn.AbstractBnnNeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.BnnNetworkData;

/**
 * BNN 神经网络（叶子）：{@link AbstractBnnNeuralNetwork} 的直系子类，无额外行为。
 * <p>
 * 两个构造路径：
 * <ul>
 *   <li>{@code (BnnNetworkData, inputSize, outputSize)}：从已加载权重构造（供 {@link #loadFromFile} 复用）。</li>
 *   <li>{@code (inputSize, outputSize)}：随机初始化（{@link BnnNetworkData#BnnNetworkData(int, int, boolean)} PCG 随机权重）。</li>
 * </ul>
 * <p>
 * {@code loadFromFile}：委托 {@link BnnNetworkData#loadFromFile}，从加载的 hyperparameters 反推 sizeA0/sizeA1。
 * <p>
 * 与原初代理 {@code BnnNeuralNetwork}（original_bnn 包）的关系：原初代理那个继承<b>原初代理的</b>
 * {@code AbstractBnnNeuralNetwork}（实现原初代理 INeuralNetwork）；本类继承<b>新版的</b>
 * {@link AbstractBnnNeuralNetwork}（实现新版 INeuralNetwork）。两者共用同一份 BNN mechanics
 * （BnnNetworkData/BnnIO/BnnNetworkProcessor 等），只是接口适配层不同。
 */
public class BnnNeuralNetwork extends AbstractBnnNeuralNetwork {

    public BnnNeuralNetwork(BnnNetworkData networkData, int inputSize, int outputSize) {
        super(networkData, inputSize, outputSize);
    }

    public BnnNeuralNetwork(int inputSize, int outputSize) {
        super(inputSize, outputSize);
    }

    /**
     * 从磁盘加载 BNN 权重，反推尺寸构造叶子实例。
     *
     * @param folderPath 权重目录（含 b_original.bin）。
     * @return 加载完成的 BnnNeuralNetwork 实例。
     */
    public static BnnNeuralNetwork loadFromFile(String folderPath) {
        BnnNetworkData net = loadNetworkData(folderPath);
        int sizeA0 = net.getHyperparameters().getSizeA0();
        int sizeA1 = net.getHyperparameters().getSizeA1();
        return new BnnNeuralNetwork(net, sizeA0, sizeA1);
    }
}