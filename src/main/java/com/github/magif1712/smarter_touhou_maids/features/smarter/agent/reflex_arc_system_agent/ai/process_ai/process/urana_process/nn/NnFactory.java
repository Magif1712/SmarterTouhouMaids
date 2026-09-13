package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.AssemblyContext;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Factory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.OutSlot;

/**
 * NN 工厂接口：把"创建一个 nn 实例"这个不实在约束，实在化为 {@code encodingProfile()} + {@code create(...)}（真善美第4条）。
 * <p>
 * <b>叶子 + 提供者</b>：nn 是组装链的叶子；nn 实例化需要尺寸（mapper/process 层 Domain 知识），
 * 故 NN 插槽的分支产物是<b>本工厂自身</b>（提供者）——消费方父工厂先经
 * {@link #encodingProfile()} 无实例查询剖面算尺寸，再经 {@link #create} 带参实例化。
 * <p>
 * 具体工厂经概念树注册进 NN 插槽（附属模组在注册事件里挂 Branch），
 * 包内代码不 import 具体工厂（真善美第3条：加新 nn 不改 urana/process）。
 */
public interface NnFactory extends Factory<NnFactory> {

    NnEncodingProfile encodingProfile();

    INeuralNetwork create(SaveSlot slot, int inputSize, int outputSize);

    /** 提供者自产：NN 插槽的分支产物即本工厂自身。 */
    @Override
    default void create(AssemblyContext ctx, /*->*/ OutSlot<NnFactory> out) {
        out.set(this);
    }

    @Override
    default Class<? extends NnFactory> producedType() {
        return NnFactory.class;
    }
}
