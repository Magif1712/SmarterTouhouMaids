package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * 布尔型 per-maid 参数项：label + tooltip + boolean getter/setter。
 * <p>
 * 与 {@link LongParamOption} 对称：值类型从 long 换为 boolean。
 * 由 RuntimeParamsPanel 渲染为 CycleButton.onOff（即时 commit，不经 commitPending）。
 * <p>
 * 典型用例：ReflexArcSystemAgentFactory 暴露"允许附身"开关
 * （附身是 ReflexArcSystemAgent 的 PossessionSensor 前置，属该 agent 特有配置）。
 */
public final class BoolParamOption implements ParamOption {
    private final Component label;
    private final Component tooltip;
    private final Predicate<EntityMaid> getter;
    private final BiConsumer<EntityMaid, Boolean> setter;

    private BoolParamOption(Component label, Component tooltip,
                            Predicate<EntityMaid> getter,
                            BiConsumer<EntityMaid, Boolean> setter) {
        this.label = label;
        this.tooltip = tooltip;
        this.getter = getter;
        this.setter = setter;
    }

    public static BoolParamOption onOff(Component label, Component tooltip,
                                        Predicate<EntityMaid> getter,
                                        BiConsumer<EntityMaid, Boolean> setter) {
        return new BoolParamOption(label, tooltip, getter, setter);
    }

    @Override
    public Component label() {
        return label;
    }

    @Override
    public Component tooltip() {
        return tooltip;
    }

    public boolean get(EntityMaid maid) {
        return getter.test(maid);
    }

    public void set(EntityMaid maid, boolean value) {
        setter.accept(maid, value);
    }
}
