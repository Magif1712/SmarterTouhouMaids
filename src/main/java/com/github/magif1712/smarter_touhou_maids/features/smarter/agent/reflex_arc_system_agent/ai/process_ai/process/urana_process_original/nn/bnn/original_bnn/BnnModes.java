package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.original_bnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Branch;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Meta;
import net.minecraft.resources.ResourceLocation;

/**
 * original_bnn 模块向 NN_LEGACY 插槽贡献的注册入口（自包含）。
 * <p>
 * bnn 是本模组「一开始」的 nn 实现（最初的二值神经网络），现自包含于 original_bnn 包：
 * 它的 id / 显示名 key / factory 实例化全部由本模块自身声明。
 * 本 Branch 由 {@link BnnRegistration} 经 Forge 事件以附属模组方式挂入 NN_LEGACY 插槽
 * （与真正附属模组的注册路径完全一致，本类即附属模组样板的一部分）。
 * <p>
 * <b>id 稳定性</b>：{@link #NN_ID} 保持 "bnn" 不变——它是稳定的逻辑/配置句柄
 * （存档、lang key、GUI 都用它）。包名/类名带「一开始」语义（original_bnn），但对外句柄不变。
 */
public final class BnnModes {
    private BnnModes() {
    }

    /** NN_LEGACY 插槽中的稳定逻辑 id（存档/lang/GUI 句柄）。包名带 original 但 id 保持 bnn。 */
    public static final String NN_ID = "bnn";

    /**
     * 构造 original_bnn 向 NN_LEGACY 插槽贡献的 Branch。
     *
     * @param modId 模组 id（用于构造 ResourceLocation 与显示名 key）。
     * @return 叶子 Branch（无 children，nn 之下无选择）。
     */
    public static Branch<NnFactory> nnBranch(String modId) {
        return new Branch<>(
                new ResourceLocation(modId, NN_ID),
                new BnnNnFactory(),
                new Meta("mode." + modId + ".nn.bnn", 0, modId));
    }
}
