package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.AssemblyContext;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Factory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.OutSlot;

/**
 * 神经网络工厂（旧版）：按尺寸创建一个 {@link INeuralNetwork} 实例。
 * <p>
 * <b>叶子 + 提供者</b>：nn 是组装链的叶子；nn 实例化需要尺寸（process 层 Domain 知识），
 * 故 NN_LEGACY 插槽的分支产物是<b>本工厂自身</b>（提供者），由 process 工厂
 * 经 {@link #encodingProfile()} 算尺寸后带参实例化。
 * <p>
 * <b>签名仅含 nn 本征尺寸 + 槽位</b>（真善美第1条"真"）：inputSize/outputSize 是任何 nn 实现都必需的
 * 本征参数。slot 供叶子从磁盘 load 已有权重（load 在 create 时——C3 时机对称）。
 * 附属 nn 若需超参数（层数/宽度/激活函数），自行读 Forge config 或自己的配置文件，
 * 不经本签名传——避免为未到来的需求加抽象，保持签名纯粹与稳定。
 * <p>
 * <b>load 失败优雅降级</b>：slot 对应目录无权重文件时，叶子 factory 应回退随机初始化
 * （首次启动/存档损坏场景）。文件存在但 load 异常时记日志并 fallback 随机。
 */
public interface NnFactory extends Factory<NnFactory> {

    /**
     * 返回该 nn 的编码剖面（各语义对象 F/B/dt/G 的载体编码长度）。
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

    /** 提供者自产：NN_LEGACY 插槽的分支产物即本工厂自身。 */
    @Override
    default void create(AssemblyContext ctx, /*->*/ OutSlot<NnFactory> out) {
        out.set(this);
    }

    @Override
    default Class<? extends NnFactory> producedType() {
        return NnFactory.class;
    }
}
