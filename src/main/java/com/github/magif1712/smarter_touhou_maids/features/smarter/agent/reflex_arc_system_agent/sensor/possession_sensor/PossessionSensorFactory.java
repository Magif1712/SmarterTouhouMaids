package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug.DebugPanelProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.debug.VisionDebugHook;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.SensorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.ISensor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 附身感受器的 {@link SensorFactory} 实现：叶子工厂，直接 new {@link PossessionSensor}。
 * <p>
 * 不查下层 registry（感受器是组装链叶子）。尺寸由上层 agent factory 从 {@code ai.feelingSize()} 算出传入。
 * <p>
 * <b>不装配解码器</b>（采集/解码分离后的正确归位）：解码器与感觉载体配对、由 ai 链的
 * nn 家族定义（fittable_mapper 层），工厂无从知晓 ai 链选了哪个 nn——解码器改由 agent
 * 从 ai 链取得、经 {@code ISensor.setVisionEncoder} 注入，配对正确性由结构保证
 * （非法组合结构上不可表达），装配期校验在 {@link PossessionSensor#setVisionEncoder} 兜底。
 * 本工厂对 feelingSize 不做校验：载体单位随 nn 家族而异（BoolVector=bit，FloatVector=元素），
 * 尺寸契约校验随解码器走（注入点），不在工厂层。
 * <p>
 * <b>调试项</b>：实现 {@link DebugPanelProvider} 暴露 Vision 调试开关（controlHint="toggle" 的 {@link ParamOption}）。
 * Vision 调试是 sensor 层（视觉采集）的内部模式，状态 per-maid 存 ParamStore（随 maid 存档走），
 * 经 {@link VisionDebugHook#KEY_VISION_DEBUG_ENABLED} 键控，消费点（onClientTick）读 ParamStore——故附身前即可配置。
 */
public class PossessionSensorFactory implements SensorFactory, DebugPanelProvider {

    /**
     * 本感受器在 SENSOR registry 的 entry id（{@code AiModeDefaults} 注册引用）。
     * smarter 代理工厂的 sensor 默认（config 未显式选择时的回退）——
     * registry 自身的默认 entry 仍是 possession_sensor（服务原初代理的旧存档回退）。
     */
    public static final ResourceLocation SENSOR_ID =
            new ResourceLocation(SmarterTouhouMaids.MOD_ID, "on_demand_possession_sensor");

    @Override
    public ISensor create(int feelingSize) {
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
