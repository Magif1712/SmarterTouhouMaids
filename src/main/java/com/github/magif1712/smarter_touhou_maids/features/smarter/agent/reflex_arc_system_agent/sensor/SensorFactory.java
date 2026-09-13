package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.AssemblyContext;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Factory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.OutSlot;

/**
 * 感受器工厂：按感觉缓冲区尺寸创建一个 {@link ISensor} 实例。
 * <p>
 * <b>叶子 + 提供者</b>：感受器是组装链的叶子（之下无选择）；感受器实例化需要跨兄弟参数
 * （feelingSize 来自 ai 层 Domain），故 SENSOR 插槽的分支产物是<b>本工厂自身</b>
 * （提供者），由 agent 工厂经 {@code ctx.child("sensor")} 取得后带参实例化
 * （{@code provider.create(ai.feelingSize())}）。
 * <p>
 * <b>签名仅含感受器本征尺寸</b>（真善美第1条"真"）：feelingSize 是任何感受器实现都必需的本征参数
 * （决定捕获分辨率与位平面排布）。附属感受器若需额外超参数，自行读 Forge config，不经本签名传——
 * 避免为未到来的需求加抽象，保持签名纯粹与稳定。
 */
public interface SensorFactory extends Factory<SensorFactory> {
    /**
     * @param feelingSize 感觉缓冲区尺寸（bits，由 ai.feelingSize() 算出传入）。
     * @return 创建好的 ISensor 实例（未 awaken，由 agent 后续调用 awaken 注入共享资源）。
     */
    ISensor create(int feelingSize);

    /** 提供者自产：SENSOR 插槽的分支产物即本工厂自身。 */
    @Override
    default void create(AssemblyContext ctx, /*->*/ OutSlot<SensorFactory> out) {
        out.set(this);
    }

    @Override
    default Class<? extends SensorFactory> producedType() {
        return SensorFactory.class;
    }
}
