package com.github.magif1712.smarter_touhou_maids.features.smarter.agent;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.IAiSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.EffectorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.SensorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.NodeKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Agent 层的插槽键常量（真善美第2条：每层只决定其下一层，不感知更下层——
 * 「我的附庸的附庸不是我的附庸」）。
 * <p>
 * 本类<b>只</b>定义 agent 层自身的插槽（{@link #AGENT}）与 agent 层决定的直接下层插槽
 * （{@link #AI}/{@link #SENSOR}/{@link #EFFECTOR}）。process/mapper/nn 等更下层插槽键
 * 分别由各层自己的 NodeKeys 定义：
 * <ul>
 *   <li>process → {@code ProcessAiNodeKeys.PROCESS}（AI 层决定）</li>
 *   <li>mapper → {@code UranaProcessNodeKeys.MAPPER}（process 层决定）</li>
 *   <li>nn → {@code FittableMapperNodeKeys.ORIGINAL_MAPPER_NN / BNN_MAPPER_NN}（per-mapper NN 插槽，mapper 层决定）</li>
 *   <li>nn_legacy → {@code urana_process_original.LegacyNodeKeys.NN_LEGACY}（旧版层决定）</li>
 * </ul>
 * 附属模组在自己的包内定义自己的插槽键，不需修改本类——只要附属 Branch 的 children
 * 指向附属自定义的插槽，GUI 自动递归展开新层级。
 * <p>
 * <b>提供者模式</b>：SENSOR/EFFECTOR 插槽的产物是工厂提供者（感受器/效应器实例化需要
 * 跨兄弟参数——feelingSize/behaviorSize 来自 ai），故类型令牌是工厂接口而非实例接口。
 * <p>
 * 持久化的 key 用 {@code NodeKey.id().toString()}（如 "smarter_touhou_maids:ai"）。
 */
public final class AgentNodeKeys {
    /** 顶层 agent 插槽：选哪个 agent 实现（smarter / 附属的别的 agent）。 */
    public static final NodeKey<IAgent> AGENT =
            new NodeKey<>(new ResourceLocation(SmarterTouhouMaids.MOD_ID, "agent"), IAgent.class);
    /** ai 插槽：选哪个 ai 实现（流程型 / 纯规则 / ...），仅当上层 agent 分支声明了该 child 时展开。 */
    public static final NodeKey<IAiSystem> AI =
            new NodeKey<>(new ResourceLocation(SmarterTouhouMaids.MOD_ID, "ai"), IAiSystem.class);
    /** 感受器插槽（提供者模式）：选哪个感受器工厂（possession_sensor[旧,推] / on_demand[新,拉] / ...）。 */
    public static final NodeKey<SensorFactory> SENSOR =
            new NodeKey<>(new ResourceLocation(SmarterTouhouMaids.MOD_ID, "sensor"), SensorFactory.class);
    /** 效应器插槽（提供者模式）：选哪个效应器工厂（bionic_muscle_effector / ...）。 */
    public static final NodeKey<EffectorFactory> EFFECTOR =
            new NodeKey<>(new ResourceLocation(SmarterTouhouMaids.MOD_ID, "effector"), EffectorFactory.class);

    private AgentNodeKeys() {
    }
}
