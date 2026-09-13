package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.AgentNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.IProcessSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.IPullEncodedSensor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Branch;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Meta;
import net.minecraft.resources.ResourceLocation;

/**
 * urana 流程模块向 PROCESS 插槽贡献的注册入口（自包含）。
 * <p>
 * urana 是<b>核心默认 process</b>（新版架构 process→mapper→nn 中 process 层的唯一默认实现）。
 * 它的 Branch 由 AI 层 {@code ProcessAiRegistration}（@EventBusSubscriber）经
 * {@link #processBranch(String)} 取用并注册，同时把 PROCESS 插槽的默认指向 {@link #PROCESS_ID}。
 * <p>
 * <b>mapper 是 process 的直接下层</b>：本 Branch 声明 child "mapper" →
 * {@link UranaProcessNodeKeys#MAPPER}，表示"选了 urana process 后还要选 mapper"。
 * <p>
 * <b>兼容性契约</b>（推论2）：urana 的载体链是 VectorBase + 拉模型握手（RefreshRequest/VisionEncoder），
 * 只兼容拉模型感受器——经 {@code addRequirement(SENSOR, IPullEncodedSensor)} 声明，
 * GUI 经 ConstraintSolver 过滤，无硬编码。
 */
public final class UranaProcessModes {
    private UranaProcessModes() {
    }

    /** PROCESS 插槽中的稳定逻辑 id（存档/lang/GUI 句柄）。 */
    public static final String PROCESS_ID = "urana";

    /**
     * 构造 urana process 向 PROCESS 插槽贡献的 Branch（含 mapper child + 拉模型感受器契约）。
     *
     * @param modId 模组 id（用于构造 ResourceLocation 与显示名 key）。
     */
    public static Branch<IProcessSystem> processBranch(String modId) {
        Branch<IProcessSystem> branch = new Branch<>(
                new ResourceLocation(modId, PROCESS_ID),
                new UranaProcessFactory(),
                new Meta("mode." + modId + ".process.urana", 0, modId));
        branch.addChild("mapper", UranaProcessNodeKeys.MAPPER);
        branch.addRequirement(AgentNodeKeys.SENSOR.id(), IPullEncodedSensor.class);
        return branch;
    }
}
