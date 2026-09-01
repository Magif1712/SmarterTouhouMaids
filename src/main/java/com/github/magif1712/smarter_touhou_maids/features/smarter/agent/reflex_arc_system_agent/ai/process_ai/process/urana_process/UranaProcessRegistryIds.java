package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import net.minecraft.resources.ResourceLocation;

/**
 * Process 层（urana_process）的 registry id 常量（真善美第2条：每层只决定其下一层，不感知更下层）。
 * <p>
 * 本类定义 process 层<b>决定的直接下层</b> id：{@link #MAPPER}（可拟合映射器 registry）。
 * process 层自身的 registry id（{@code smarter_touhou_maids:process}）由上层 AI 层的
 * {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.ProcessAiRegistryIds#PROCESS}
 * 定义——父层决定子层 id，子层引用父层定义（上→下决定，下→上引用）。
 * <p>
 * 附属模组可在自己的 process 实现包内定义自己的直接下层 id（如 process→custom_layer→nn），
 * 不需修改本类——只要附属 process entry 的 subRegistryId 指向附属自定义的 registry id，
 * GUI 自动递归展开新层级。
 */
public final class UranaProcessRegistryIds {
    /** 可拟合映射器 registry：选哪个 mapper 实现（urana / ...），仅当上层 process 需要 mapper 时展开。
     *  由 process 层决定（process 层的直接下层），mapper 层引用此 id 注册 MapperRegistry。 */
    public static final ResourceLocation MAPPER = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "mapper");

    private UranaProcessRegistryIds() {
    }
}
