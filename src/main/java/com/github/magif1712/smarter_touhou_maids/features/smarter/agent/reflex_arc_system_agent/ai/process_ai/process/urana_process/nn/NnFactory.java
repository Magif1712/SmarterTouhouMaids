package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn;

/**
 * 神经网络工厂：按尺寸创建一个 {@link INeuralNetwork} 实例。
 * <p>
 * <b>叶子工厂</b>：nn 是组装链的叶子，不查下层 registry（nn 之下无选择）。
 * <p>
 * <b>签名仅含 nn 本征尺寸</b>（真善美第1条"真"）：inputSize/outputSize 是任何 nn 实现都必需的
 * 本征参数。附属 nn 若需超参数（层数/宽度/激活函数），自行读 Forge config 或自己的配置文件，
 * 不经本签名传——避免为未到来的需求加抽象，保持签名纯粹与稳定。
 * <p>
 * 尺寸由上层 process factory 算出传入（尺寸是 process 层 Domain 知识，不是 nn 知识）。
 */
@FunctionalInterface
public interface NnFactory {
    /**
     * @param inputSize  输入向量尺寸（由 process 层 Domain 算出传入）。
     * @param outputSize 输出向量尺寸。
     * @return 创建好的 INeuralNetwork 实例。
     */
    INeuralNetwork create(int inputSize, int outputSize);
}
