package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;

import java.util.function.ObjLongConsumer;
import java.util.function.ToLongFunction;

/**
 * 数值型 per-maid 参数项：label + tooltip + long getter/setter。
 * <p>
 * 原 {@link ParamOption} 类的数值内容（迁移自旧 ParamOption.of）。
 * 由 RuntimeParamsPanel 渲染为 EditBox，commit 时 parse long。
 */
public final class LongParamOption implements ParamOption {
    private final Component label;
    private final Component tooltip;
    private final ToLongFunction<EntityMaid> getter;
    private final ObjLongConsumer<EntityMaid> setter;

    private LongParamOption(Component label, Component tooltip,
                            ToLongFunction<EntityMaid> getter,
                            ObjLongConsumer<EntityMaid> setter) {
        this.label = label;
        this.tooltip = tooltip;
        this.getter = getter;
        this.setter = setter;
    }

    public static LongParamOption of(Component label, Component tooltip,
                                     ToLongFunction<EntityMaid> getter,
                                     ObjLongConsumer<EntityMaid> setter) {
        return new LongParamOption(label, tooltip, getter, setter);
    }

    @Override
    public Component label() {
        return label;
    }

    @Override
    public Component tooltip() {
        return tooltip;
    }

    public long get(EntityMaid maid) {
        return getter.applyAsLong(maid);
    }

    public void set(EntityMaid maid, long value) {
        setter.accept(maid, value);
    }
}
