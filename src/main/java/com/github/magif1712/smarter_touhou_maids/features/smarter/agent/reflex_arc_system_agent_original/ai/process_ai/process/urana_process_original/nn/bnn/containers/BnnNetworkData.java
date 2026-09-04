package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers;

import java.io.IOException;

public class BnnNetworkData implements AutoCloseable {
    private BnnHyperparameters hyperparameters_original;

    public BnnNetworkData(int sizeA0, int sizeA1, boolean overwrite) throws IOException {
        this.hyperparameters_original = new BnnHyperparameters(sizeA0, sizeA1);
    }

    /**
     * 将模型序列化到磁盘。
     */
    public void save(String folderPath) {
        hyperparameters_original.save(folderPath);
    }

    /**
     * 从磁盘自动加载模型，无需手动指定维度。
     */
    public static BnnNetworkData loadFromFile(String folderPath) {
        BnnHyperparameters hp = BnnHyperparameters.loadFromFile(folderPath);
        return new BnnNetworkData(hp);
    }

    private BnnNetworkData(BnnHyperparameters hyperparameters) {
        this.hyperparameters_original = hyperparameters;
    }

    public BnnHyperparameters getHyperparameters() {
        return hyperparameters_original;
    }

    @Override
    public void close() throws Exception {
        hyperparameters_original.close();
    }
}
