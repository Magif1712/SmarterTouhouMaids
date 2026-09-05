package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.cnn.containers;

/**
 * CNN 网络数据：把"网络 = 超参"这个不实在约束，实在化为一个对象（真善美第4条）。
 * <p>
 * 两个构造路径：按 (sizeA0, sizeA1) 新建（PCG 随机初始化 p/q/l/r/b），
 * 或直接注入已加载的 {@link CnnHyperparameters}。{@code save} 委托超参保存语义权重，
 * {@code loadFromFile} 委托超参加载（idx/w 由调用方后续 {@code cnnRefreshCache} 重算）。
 * {@code close()} 真实释放超参（含派生缓存 idx/w）。
 */
public class CnnNetworkData implements AutoCloseable {
    private final CnnHyperparameters hyperparameters;

    public CnnNetworkData(int sizeA0, int sizeA1) {
        this.hyperparameters = new CnnHyperparameters(sizeA0, sizeA1);
    }

    public CnnNetworkData(CnnHyperparameters hyperparameters) {
        this.hyperparameters = hyperparameters;
    }

    public void save(String folderPath) {
        this.hyperparameters.save(folderPath);
    }

    public static CnnNetworkData loadFromFile(String folderPath) {
        CnnHyperparameters hp = CnnHyperparameters.loadFromFile(folderPath);
        return new CnnNetworkData(hp);
    }

    public CnnHyperparameters getHyperparameters() {
        return hyperparameters;
    }

    @Override
    public void close() throws Exception {
        hyperparameters.close();
    }
}