package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.original_bnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;

/**
 * original_bnn 模块向 NnRegistry 贡献的注册入口（自包含）。
 * <p>
 * bnn 是本模组「一开始」的 nn 实现（最初的二值神经网络），现自包含于 original_bnn 包：
 * 它的 id / 显示名 key / factory 实例化全部由本模块自身声明。
 * 本 entry 由 {@link BnnRegistration} 经 Forge 事件以附属模组方式注册进 NnRegistry
 * （与真正附属模组的注册路径完全一致，本类即附属模组样板的一部分）。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：「bnn 是一个自包含的 nn 模块」这个模式落在 original_bnn 包内
 *       （实现 + 注册贡献者），不落在 AiModeDefaults 里。</li>
 *   <li><b>第3条</b>：上层（urana/process）通过 NnRegistry 取 NnFactory，不依赖本类；
 *       切换或删除本模块时上层零改动。</li>
 *   <li><b>第4条</b>：「自包含」这个不实在约束，实在化为 {@link #nnEntry(String)} 这个有签名的方法。</li>
 * </ul>
 * <p>
 * <b>registry id 稳定性</b>：{@link #NN_ID} 保持 "bnn" 不变——它是稳定的逻辑/配置句柄
 * （存档、lang key、GUI 都用它）。包名/类名带「一开始」语义（original_bnn），但对外句柄不变。
 */
public final class BnnModes {
    private BnnModes() {
    }

    /** NnRegistry 中的稳定逻辑 id（存档/lang/GUI 句柄）。包名带 original 但 id 保持 bnn，避免破坏既有配置。 */
    public static final String NN_ID = "bnn";

    /**
     * 构造 original_bnn 向 NnRegistry 贡献的 entry。
     *
     * @param modId 模组 id（用于构造 ResourceLocation 与显示名 key）。
     * @return 叶子 entry（subRegistryId=null，nn 之下无选择）。
     */
    public static RegistryEntry<NnFactory> nnEntry(String modId) {
        return new RegistryEntry<>(
                new ResourceLocation(modId, NN_ID),
                "mode." + modId + ".nn.bnn",
                new BnnNnFactory(),
                null); // 叶子，无下层
    }
}
