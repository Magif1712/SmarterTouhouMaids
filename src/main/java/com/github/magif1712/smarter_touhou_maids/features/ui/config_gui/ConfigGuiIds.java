package com.github.magif1712.smarter_touhou_maids.features.ui.config_gui;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.NodeKey;
import net.minecraft.resources.ResourceLocation;

/**
 * GUI 域的插槽键常量（与 smarter 模式域平行的域根）。
 * <p>
 * GUI 域的插槽不属于 smarter 模式组装链层级（agent/ai/process/nn/sensor/effector），
 * 故独立于 {@code AgentNodeKeys}——保持两域各自的语义纯净（真善美第2条：C 中没有的 D 中也没有）。
 * 两域共用同一棵概念树（同一快照），以各自的根插槽区分（多根 freeze）。
 */
public final class ConfigGuiIds {
    /** 配置 GUI 插槽（GUI 域根）：选哪种配置界面打开（客户端专用，FMLClientSetupEvent 注册 Branch）。
     *  与 AGENT 同构（多 Branch + defaultBranch），但仅客户端存在——Screen 是客户端对象。 */
    public static final NodeKey<ConfigGuiFactory> CONFIG_GUI =
            new NodeKey<>(new ResourceLocation(SmarterTouhouMaids.MOD_ID, "config_gui"), ConfigGuiFactory.class);

    private ConfigGuiIds() {
    }
}
