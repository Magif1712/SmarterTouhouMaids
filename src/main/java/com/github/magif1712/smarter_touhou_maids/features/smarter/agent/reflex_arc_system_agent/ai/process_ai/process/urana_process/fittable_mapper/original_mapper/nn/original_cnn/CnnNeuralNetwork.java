package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.original_mapper.nn.original_cnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn.AbstractCnnNeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn.containers.CnnHyperparameters;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn.containers.CnnNetworkData;

/**
 * 朴素 CNN 神经网络（叶子）：{@link AbstractCnnNeuralNetwork} 的直系子类，无额外行为。
 * <p>
 * 两个构造路径：{@code (inputSize, outputSize, networkData)}（networkData 可 null），
 * 与便利构造 {@code (inputSize, outputSize)}（等价于 networkData=null）。
 * <p>
 * {@code loadFromFile}：从加载的 hp 反推 inputSize=sizeA0、outputSize=sizeA1。
 */
public class CnnNeuralNetwork extends AbstractCnnNeuralNetwork {

    public CnnNeuralNetwork(int inputSize, int outputSize, CnnNetworkData networkData) {
        super(inputSize, outputSize, networkData);
    }

    public CnnNeuralNetwork(int inputSize, int outputSize) {
        this(inputSize, outputSize, null);
    }

    public static CnnNeuralNetwork loadFromFile(String folderPath) {
        CnnNetworkData net = CnnNetworkData.loadFromFile(folderPath);
        CnnHyperparameters hp = net.getHyperparameters();
        return new CnnNeuralNetwork(hp.getSizeA0(), hp.getSizeA1(), net);
    }
}