package com.github.magif1712.smarter_touhou_maids.features.ui.config_gui;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import net.minecraft.resources.ResourceLocation;

/**
 * GUI 域 registry 的 id 常量（真善美第3条：把"GUI 域有哪些 registry"实在化为常量）。
 * <p>
 * 与 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryIds}
 * 平行：后者是 smarter 模式域各层 registry 的 id 集合，本类是 GUI 域的。
 * <p>
 * GUI 域的 registry key 不属于 smarter 模式组装链层级（agent/ai/process/nn/sensor/effector），
 * 故独立于此处而非混入 RegistryIds——保持两域各自的语义纯净（真善美第2条：C 中没有的 D 中也没有）。
 */
public final class ConfigGuiIds {
    /** 配置 GUI registry：选哪种配置界面打开（客户端专用，FMLClientSetupEvent 注册 entries）。
     *  与 AGENT/AI 同构（多选一+default），但仅客户端存在——Screen 是客户端对象。 */
    public static final ResourceLocation CONFIG_GUI = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "config_gui");

    private ConfigGuiIds() {
    }
}
