package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.original_mapper.nn.original_cnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Branch;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Meta;
import net.minecraft.resources.ResourceLocation;

/**
 * cnn 模块向 NN 插槽贡献的注册入口（自包含）。
 * <p>
 * cnn 是<b>核心默认 nn</b>（新版架构 process→mapper→nn 中 nn 层的唯一默认实现，
 * 即朴素 CNN——BNN 的浮点版）。它的 Branch 由 process 层注册处经 {@link #nnBranch(String)}
 * 取用并挂入 original_mapper 的专属 NN 插槽，同时把该插槽默认指向 {@link #NN_ID}。
 * <p>
 * 设计原则（真善美）：「cnn 是一个自包含的 nn 模块」这个模式落在 cnn 包内
 * （实现 + 注册贡献者）；上层只决定「默认 nn id」与「捆绑哪些 nn 模块」，
 * 不内联 factory 或 id 字符串。
 */
public final class CnnNnModes {
    private CnnNnModes() {
    }

    /** NN 插槽中的稳定逻辑 id（存档/lang/GUI 句柄）。 */
    public static final String NN_ID = "cnn";

    /**
     * 构造 cnn 向 NN 插槽贡献的 Branch。
     *
     * @param modId 模组 id（用于构造 ResourceLocation 与显示名 key）。
     * @return 叶子 Branch（无 children，nn 之下无选择）。
     */
    public static Branch<NnFactory> nnBranch(String modId) {
        return new Branch<>(
                new ResourceLocation(modId, NN_ID),
                new CnnNnFactory(),
                new Meta("mode." + modId + ".nn.cnn", 0, modId));
    }
}
