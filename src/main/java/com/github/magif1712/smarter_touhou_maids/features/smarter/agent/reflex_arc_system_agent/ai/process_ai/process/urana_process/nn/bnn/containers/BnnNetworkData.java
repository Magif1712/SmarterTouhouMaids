package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers;

import java.io.IOException;

public class BnnNetworkData implements AutoCloseable {
    private BnnHyperparameters hyperparameters;

    public BnnNetworkData(int sizeA0, int sizeA1, boolean overwrite) throws IOException {
        this.hyperparameters = new BnnHyperparameters(sizeA0, sizeA1);
    }

    /**
     * 将模型序列化到磁盘。
     */
    public void save(String folderPath) {
        hyperparameters.save(folderPath);
    }

    /**
     * 从磁盘自动加载模型，无需手动指定维度。
     */
    public static BnnNetworkData loadFromFile(String folderPath) {
        BnnHyperparameters hp = BnnHyperparameters.loadFromFile(folderPath);
        return new BnnNetworkData(hp);
    }

    private BnnNetworkData(BnnHyperparameters hyperparameters) {
        this.hyperparameters = hyperparameters;
    }

    public BnnHyperparameters getHyperparameters() {
        return hyperparameters;
    }

    @Override
    public void close() throws Exception {
        hyperparameters.close();
    }
}