package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;

/**
 * 神经网络工厂：按尺寸创建一个 {@link INeuralNetwork} 实例。
 * <p>
 * <b>叶子工厂</b>：nn 是组装链的叶子，不查下层 registry（nn 之下无选择）。
 * <p>
 * <b>签名仅含 nn 本征尺寸 + 槽位</b>（真善美第1条"真"）：inputSize/outputSize 是任何 nn 实现都必需的
 * 本征参数。slot 供叶子从磁盘 load 已有权重（load 在 create 时——C3 时机对称）。
 * 附属 nn 若需超参数（层数/宽度/激活函数），自行读 Forge config 或自己的配置文件，
 * 不经本签名传——避免为未到来的需求加抽象，保持签名纯粹与稳定。
 * <p>
 * 尺寸由上层 process factory 算出传入（尺寸是 process 层 Domain 知识，不是 nn 知识）。
 * <p>
 * <b>load 失败优雅降级</b>：slot 对应目录无权重文件时，叶子 factory 应回退随机初始化
 * （首次启动/存档损坏场景）。文件存在但 load 异常时记日志并 fallback 随机。
 */
public interface NnFactory {

    /**
     * 返回该 nn 的编码剖面（各语义对象 F/B/dt/G 的载体编码长度）。
     * <p>
     * 设计原则（真善美第2/3条）：长度是 nn 载体编码的属性，不是 urana 意识域的模式。
     * urana 的 domain 收本 profile + 自己的倍数关系（C=F×3, G=4方位）算 span。
     * 换 nn 实现（BNN→CNN）时，新 factory 返回自己的 profile，urana 零改动。
     * <p>
     * <b>无实例查询</b>（破鸡生蛋）：在 {@link #create} 之前调用，供 urana 算 inputSize/outputSize
     * （算 total 需要 profile，而 nn 实例尚未创建）。与 {@link INeuralNetwork#encodingProfile()}
     * 对称：factory 级供实例化前，实例级供实例化后，两者返回同一个值。
     *
     * @return 该 nn 的编码剖面
     */
    NnEncodingProfile encodingProfile();

    /**
     * @param slot       持久化槽位（供 load 已有权重；slot 目录无文件时 fallback 随机初始化）。
     * @param inputSize  输入向量尺寸（由 process 层 Domain 用 profile 算出传入）。
     * @param outputSize 输出向量尺寸。
     * @return 创建好的 INeuralNetwork 实例（已 load 或随机初始化）。
     */
    INeuralNetwork create(SaveSlot slot, int inputSize, int outputSize);
}
