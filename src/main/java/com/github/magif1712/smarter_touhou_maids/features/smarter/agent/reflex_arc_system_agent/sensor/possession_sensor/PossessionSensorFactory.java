package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug.DebugPanelProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.debug.VisionDebugHook;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.SensorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.ISensor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.vision.VisionOps;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 附身感受器的 {@link SensorFactory} 实现：叶子工厂，直接 new {@link PossessionSensor}。
 * <p>
 * 不查下层 registry（感受器是组装链叶子）。尺寸由上层 agent factory 从 {@code ai.feelingSize()} 算出传入。
 * <p>
 * 校验 feelingSize == AI_WIDTH * AI_HEIGHT * 24（1920*1080*24）：这是 vision 子模式的固定视网膜
 * 分辨率与位平面排布（R/G/B 各 8 bit 平面 = 24 bit/pixel）。尺寸不符直接抛异常——
 * 真善美第3条：把"尺寸契约"这个不实在的约束，用实在的运行时校验固化。
 * <p>
 * <b>调试项</b>：实现 {@link DebugPanelProvider} 暴露 Vision 调试开关（controlHint="toggle" 的 {@link ParamOption}）。
 * Vision 调试是 sensor 层（视觉采集）的内部模式，状态 per-maid 存 ParamStore（随 maid 存档走），
 * 经 {@link VisionDebugHook#KEY_VISION_DEBUG_ENABLED} 键控，消费点（onClientTick）读 ParamStore——故附身前即可配置。
 */
public class PossessionSensorFactory implements SensorFactory, DebugPanelProvider {

    @Override
    public ISensor create(int feelingSize) {
        int expected = VisionOps.AI_WIDTH * VisionOps.AI_HEIGHT * 24;
        if (feelingSize != expected) {
            throw new IllegalArgumentException(
                    "Invalid feeling size for PossessionSensor: " + feelingSize
                            + " (expected " + expected + " = " + VisionOps.AI_WIDTH
                            + "*" + VisionOps.AI_HEIGHT + "*24)");
        }
        return new PossessionSensor();
    }

    @Override
    public List<ParamOption> getDebugOptions() {
        return List.of(
                ParamOption.persistable(
                        Component.translatable("debug.smarter_touhou_maids.vision"),
                        Component.translatable("debug.smarter_touhou_maids.vision.tooltip"),
                        VisionDebugHook.KEY_VISION_DEBUG_ENABLED, "false")
                        .withControlHint("toggle"));
    }
}
