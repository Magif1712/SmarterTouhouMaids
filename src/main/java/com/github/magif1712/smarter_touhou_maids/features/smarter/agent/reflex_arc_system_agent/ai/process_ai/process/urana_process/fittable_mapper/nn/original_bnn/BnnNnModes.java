package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_bnn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;

/**
 * original_bnn 模块向 NnRegistry 贡献的注册入口（自包含，新版架构）。
 * <p>
 * bnn 是"原初代理同款 BNN"在新版架构下的 nn 实现（位运算，BoolVector 载体），
 * 与 {@code CnnNnModes}（cnn，浮点，FloatVector 载体）并列，供 GPU 占用对照测试切换。
 * <p>
 * <b>非默认</b>：默认 nn 仍是 cnn（{@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.CnnNnModes#NN_ID}）；
 * 本 entry 仅注册为可选项，测试时在 GUI 手动切换 nn 到 {@link #NN_ID}。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：「bnn 是一个自包含的 nn 模块」这个模式落在 original_bnn 包内
 *       （实现 + 注册贡献者）。</li>
 *   <li><b>第3条</b>：上层（mapper/process）通过 NnRegistry 取 NnFactory，不依赖本类；
 *       切换或删除本模块时上层零改动——"上层 ai 系统中的神经网络 nn 可以切换到 bnn 也可以切换到 cnn"。</li>
 *   <li><b>第4条</b>：「自包含」这个不实在约束，实在化为 {@link #nnEntry(String)} 这个有签名的方法。</li>
 * </ul>
 * <p>
 * <b>registry id 稳定性</b>：{@link #NN_ID} = "bnn"，与原初代理同款，保证权重文件名
 * （{@code b_original.bin}）与配置句柄跨代理一致。
 */
public final class BnnNnModes {
    private BnnNnModes() {
    }

    /** NnRegistry 中的稳定逻辑 id（存档/lang/GUI 句柄）。与原初代理同款。 */
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
