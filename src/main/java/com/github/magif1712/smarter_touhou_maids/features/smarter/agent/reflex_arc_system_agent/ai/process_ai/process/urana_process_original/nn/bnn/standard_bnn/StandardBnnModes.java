package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.standard_bnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Branch;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Meta;
import net.minecraft.resources.ResourceLocation;

/**
 * standard_bnn 模块向 NN_LEGACY 插槽贡献的注册入口（自包含）。
 * <p>
 * standard_bnn 是<b>旧版核心默认 nn</b>（带输入变化门控重连的 bnn，即惊跳反射）。
 * 它的 Branch 由旧版层注册处经 {@link #nnBranch(String)} 取用并挂入 NN_LEGACY 插槽，
 * 同时把该插槽默认指向 {@link #NN_ID}。
 */
public final class StandardBnnModes {
    private StandardBnnModes() {
    }

    /** NN_LEGACY 插槽中的稳定逻辑 id（存档/lang/GUI 句柄）。 */
    public static final String NN_ID = "standard_bnn";

    /**
     * 构造 standard_bnn 向 NN_LEGACY 插槽贡献的 Branch。
     *
     * @param modId 模组 id（用于构造 ResourceLocation 与显示名 key）。
     * @return 叶子 Branch（无 children，nn 之下无选择）。
     */
    public static Branch<NnFactory> nnBranch(String modId) {
        return new Branch<>(
                new ResourceLocation(modId, NN_ID),
                new StandardBnnNnFactory(),
                new Meta("mode." + modId + ".nn.standard_bnn", 0, modId));
    }
}
