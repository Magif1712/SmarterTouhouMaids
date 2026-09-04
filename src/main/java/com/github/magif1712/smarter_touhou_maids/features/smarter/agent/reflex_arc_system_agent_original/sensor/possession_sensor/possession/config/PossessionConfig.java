package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.sensor.possession_sensor.possession.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 原初代理附身配置——复用新版代理的全局配置（真善美第2条：附身开关是全局配置，
 * 不按代理分支区分；原初代理的 register 不被 ModClientConfig 调用，enabled 直接
 * 引用新版代理的 BooleanValue 实例，类加载时 ModClientConfig 已初始化完毕）。
 */
public class PossessionConfig {
    public static ForgeConfigSpec.BooleanValue enabled =
            com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.config.PossessionConfig.enabled;

    public static void register(ForgeConfigSpec.Builder builder) {
        // no-op：附身配置全局唯一，由新版代理的 PossessionConfig.register 注册
    }
}
