package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class PossessionConfig {
    public static ForgeConfigSpec.BooleanValue enabled;

    public static void register(ForgeConfigSpec.Builder builder) {
        builder.push("possession");
        enabled = builder
                .comment("Enable 'Possess and become smarter' feature. This is now the global default value for whether a maid can be possessed. This option only takes effect if the maid has not been set individually.")
                .define("enabled", true);
        builder.pop();
    }
}