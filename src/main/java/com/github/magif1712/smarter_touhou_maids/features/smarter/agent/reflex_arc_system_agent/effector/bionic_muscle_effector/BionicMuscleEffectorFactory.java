package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.bionic_muscle_effector;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug.DebugOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug.DebugPanelProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.debug.EffectorDebugHook;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.EffectorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.IEffector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.bionic_muscle_effector.semantics.PolarLayout;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 仿生肌肉效应器的 {@link EffectorFactory} 实现：叶子工厂，直接 new {@link BionicMuscleEffector}。
 * <p>
 * 不查下层 registry（效应器是组装链叶子）。尺寸由上层 agent factory 从 {@code ai.behaviorSize()} 算出传入。
 * <p>
 * 校验 behaviorSize == 256：这是仿生肌肉布局（拮抗肌对 + 独立肌群 + HOTBAR one-hot）的固定位宽。
 * 尺寸不符直接抛异常——真善美第3条：把"尺寸契约"这个不实在的约束，用实在的运行时校验固化。
 * <p>
 * <b>调试项</b>：实现 {@link DebugPanelProvider} 暴露 Effector 调试开关。
 * Effector 调试是 effector 层（效应器输出）的内部模式，状态存储在 {@link EffectorDebugHook} 单例（不依赖 agent 实例），
 * 故附身前即可配置。
 */
public class BionicMuscleEffectorFactory implements EffectorFactory, DebugPanelProvider {

    @Override
    public IEffector create(int behaviorSize) {
        if (behaviorSize != 256) {
            throw new IllegalArgumentException(
                    "Invalid behavior size for BionicMuscleEffector: " + behaviorSize + " (expected 256)");
        }
        return new BionicMuscleEffector(PolarLayout.defaultHumanLike(), behaviorSize);
    }

    @Override
    public List<DebugOption> getDebugOptions() {
        return List.of(
                DebugOption.onOff(
                        Component.translatable("debug.smarter_touhou_maids.effector"),
                        Component.translatable("debug.smarter_touhou_maids.effector.tooltip"),
                        EffectorDebugHook.INSTANCE::isEnabled,
                        EffectorDebugHook.INSTANCE::setEnabled));
    }
}
