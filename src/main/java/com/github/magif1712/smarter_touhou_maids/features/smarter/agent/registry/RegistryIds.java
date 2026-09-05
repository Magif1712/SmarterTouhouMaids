package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import net.minecraft.resources.ResourceLocation;

/**
 * Agent 层的 registry id 常量（真善美第2条：每层只决定其下一层，不感知更下层——
 * 「我的附庸的附庸不是我的附庸」）。
 * <p>
 * 本类<b>只</b>定义 agent 层自身的 id（{@link #AGENT}）与 agent 层决定的直接下层 id
 * （{@link #AI}/{@link #SENSOR}/{@link #EFFECTOR}）。process/mapper/nn/nn_legacy 等
 * 更下层 id 分别由各层自己的 RegistryIds 定义：
 * <ul>
 *   <li>process → {@code ProcessAiRegistryIds.PROCESS}（AI 层决定）</li>
 *   <li>mapper → {@code UranaProcessRegistryIds.MAPPER}（process 层决定）</li>
 *   <li>nn → {@code FittableMapperRegistryIds.ORIGINAL_MAPPER_NN} / {@code BNN_MAPPER_NN}（per-mapper NN registry，mapper 层决定）</li>
 *   <li>nn_legacy → {@code LegacyRegistryIds.NN_LEGACY}（旧版层决定）</li>
 * </ul>
 * 附属模组在自己的包内定义自己的层级 id，不需修改本类——只要附属 entry 的 subRegistryId
 * 指向附属自定义的 registry id，GUI 自动递归展开新层级路径。
 * <p>
 * config 的 key 用 {@code registryId.toString()}（如 "smarter_touhou_maids:ai"）。
 * <p>
 * GUI 域的 registry id 在 {@link com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.ConfigGuiIds}，
 * 不在此处——GUI 域与 smarter 模式域是两个平行的域。
 */
public final class RegistryIds {
    /** 顶层 agent registry：选哪个 agent 实现（smarter / 附属的别的 agent） */
    public static final ResourceLocation AGENT = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "agent");
    /** ai registry：选哪个 ai 实现（流程型 / 纯规则 / ...），仅当上层 agent 需要 ai 时展开。
     *  由 agent 层决定（agent 层的直接下层），AI 层引用此 id 注册 AiRegistry。
     *  原初代理（smarter_original）使用此 registry——只含 urana_original 流程。 */
    public static final ResourceLocation AI = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "ai");
    /** ai_smarter registry：新版代理（smarter）的独立 AI registry——只含 urana 流程，
     *  与原初代理的 AI registry 隔离（跨代理流程不可选，避免不兼容组合）。
     *  由 agent 层决定（agent 层的直接下层），AI 层引用此 id 注册 AiRegistry。 */
    public static final ResourceLocation AI_SMARTER = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "ai_smarter");
    /** 感受器 registry：选哪个感受器实现（possession_sensor / ...），与 ai 并列（agent 下 sensor+ai+effector） */
    public static final ResourceLocation SENSOR = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "sensor");
    /** 效应器 registry：选哪个效应器实现（bionic_muscle_effector / ...），与 ai 并列（agent 下 sensor+ai+effector） */
    public static final ResourceLocation EFFECTOR = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "effector");

    private RegistryIds() {
    }
}