package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import net.minecraft.resources.ResourceLocation;

/**
 * smarter 模式组装链各层 registry 的 id 常量（真善美第3条：把"有哪些层"实在化为常量）。
 * <p>
 * config 的 key 用 {@code registryId.toString()}（如 "smarter_touhou_maids:ai"），
 * 各层 factory 按需读对应 key。附属模组用自己的 mod id 命名空间注册新 registry。
 * <p>
 * GUI 域的 registry id 在 {@link com.github.magif1712.smarter_touhou_maids.features.ui.ConfigGuiIds}，
 * 不在此处——GUI 域与 smarter 模式域是两个平行的域。
 */
public final class RegistryIds {
    /** 顶层 agent registry：选哪个 agent 实现（smarter / 附属的别的 agent） */
    public static final ResourceLocation AGENT = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "agent");
    /** ai registry：选哪个 ai 实现（流程型 / 纯规则 / ...），仅当上层 agent 需要 ai 时展开 */
    public static final ResourceLocation AI = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "ai");
    /** 流程系统 registry：选哪个 process 实现（urana / ...），仅当上层 ai 需要 process 时展开 */
    public static final ResourceLocation PROCESS = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "process");
    /** 神经网络 registry：选哪个 nn 实现（bnn / cnn / ...），仅当上层 process 需要 nn 时展开 */
    public static final ResourceLocation NN = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "nn");
    /** 感受器 registry：选哪个感受器实现（possession_sensor / ...），与 ai 并列（agent 下 sensor+ai+effector） */
    public static final ResourceLocation SENSOR = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "sensor");
    /** 效应器 registry：选哪个效应器实现（bionic_muscle_effector / ...），与 ai 并列（agent 下 sensor+ai+effector） */
    public static final ResourceLocation EFFECTOR = new ResourceLocation(SmarterTouhouMaids.MOD_ID, "effector");

    private RegistryIds() {
    }
}
