package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.bnn_mapper.nn.original_bnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Branch;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Meta;
import net.minecraft.resources.ResourceLocation;

/**
 * original_bnn 模块向 NN 插槽贡献的注册入口（自包含，新版架构）。
 * <p>
 * bnn 是"原初代理同款 BNN"在新版架构下的 nn 实现（位运算，BoolVector 载体），
 * 与 {@code CnnNnModes}（cnn，浮点，FloatVector 载体）并列，供 GPU 占用对照测试切换。
 * <p>
 * <b>非默认</b>：默认 nn 仍是 cnn；本 Branch 仅注册为可选项。
 * <p>
 * <b>id 稳定性</b>：{@link #NN_ID} = "bnn"，与原初代理同款，保证权重文件名
 * （{@code b_original.bin}）与配置句柄跨代理一致。
 */
public final class BnnNnModes {
    private BnnNnModes() {
    }

    /** NN 插槽中的稳定逻辑 id（存档/lang/GUI 句柄）。与原初代理同款。 */
    public static final String NN_ID = "bnn";

    /**
     * 构造 original_bnn 向 NN 插槽贡献的 Branch。
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
