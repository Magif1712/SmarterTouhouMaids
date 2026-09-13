package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.AssemblyContext;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Factory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.OutSlot;

/**
 * 效应器工厂：按行为向量尺寸创建一个 {@link IEffector} 实例。
 * <p>
 * <b>叶子 + 提供者</b>（镜像 {@code SensorFactory}）：效应器是组装链的叶子；
 * 实例化需要跨兄弟参数（behaviorSize 来自 ai 层 Domain），故 EFFECTOR 插槽的
 * 分支产物是<b>本工厂自身</b>（提供者），由 agent 工厂带参实例化。
 * <p>
 * <b>签名仅含效应器本征尺寸</b>（真善美第1条"真"）：附属效应器若需额外超参数，
 * 自行读 Forge config，不经本签名传。
 */
public interface EffectorFactory extends Factory<EffectorFactory> {
    /**
     * @param behaviorSize 行为向量尺寸（bits，由 ai.behaviorSize() 算出传入）。
     * @return 创建好的 IEffector 实例（未 awaken，由 agent 后续调用 awaken 初始化肌肉状态）。
     */
    IEffector create(int behaviorSize);

    /** 提供者自产：EFFECTOR 插槽的分支产物即本工厂自身。 */
    @Override
    default void create(AssemblyContext ctx, /*->*/ OutSlot<EffectorFactory> out) {
        out.set(this);
    }

    @Override
    default Class<? extends EffectorFactory> producedType() {
        return EffectorFactory.class;
    }
}
