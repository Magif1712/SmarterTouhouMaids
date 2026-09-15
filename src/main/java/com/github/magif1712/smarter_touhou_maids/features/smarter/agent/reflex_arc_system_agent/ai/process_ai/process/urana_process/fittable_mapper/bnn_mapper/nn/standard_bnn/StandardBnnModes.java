package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.bnn_mapper.nn.standard_bnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Branch;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Meta;
import net.minecraft.resources.ResourceLocation;

/**
 * standard_bnn（惊跳反射）向 bnn_mapper 专属 NN 插槽贡献的注册入口（自包含）。
 * <p>
 * standard_bnn 是带输入变化门控重连的 bnn（惊跳反射）：静止期安静睡眠，环境变化精确恢复
 * 信号通路。它与 bnn（original_bnn，无门控）并列于 {@code FittableMapperNodeKeys#BNN_MAPPER_NN}
 * 插槽，由 {@code StandardBnnRegistration} 经 {@code RegistryCollectEvent} 以附属模组式追加——
 * <b>只 addBranch、不改该插槽 defaultBranch</b>（本模块是选项而非默认，默认仍是 bnn）。
 * <p>
 * <b>id 稳定性</b>：{@link #NN_ID} = "standard_bnn"，与旧版 urana 同款（旧版 NN_LEGACY 插槽
 * 亦存在同名分支——Branch id 唯一性按 Node 隔离校验（{@code Node.addBranch} 的
 * putIfAbsent），两插槽互不冲突）。lang 键 {@code mode.<modid>.nn.standard_bnn}
 * （zh：「标准二值神经网络（惊跳反射）」/ en："Standard BNN (Startle Reflex)"）已存在，直接复用。
 * <p>
 * 设计原则（真善美第3条）：把「可选门控重连 bnn」这个不实在约束，实在化为 Branch 工厂对象；
 * 删换本模块只删本包，上层零改动。
 */
public final class StandardBnnModes {
    private StandardBnnModes() {
    }

    /** NN 插槽中的稳定逻辑 id（存档/lang/GUI 句柄）。与旧版 urana 同款。 */
    public static final String NN_ID = "standard_bnn";

    /**
     * 构造 standard_bnn 向 NN 插槽贡献的 Branch。
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
