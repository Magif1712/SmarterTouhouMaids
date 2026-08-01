package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import net.minecraft.nbt.CompoundTag;

/**
 * 流程系统工厂：按配置创建一个 {@link IProcessSystem} 实例。
 * <p>
 * <b>工厂自驱组装</b>（真善美第1条"真"）：本工厂直接使用 nn（UranaSystem 构造注入 INeuralNetwork），
 * 故自行查 {@code NnRegistry} 取下层 nn factory 并创建 nn，再 new UranaSystem(nn, ...)。
 * <p>
 * <b>nn 尺寸归属 process 层</b>：inputSize/outputSize 是 process 的 Domain 知识
 * （urana 用 InputVectorDomain.TOTAL_LENGTH 等），由本工厂算出后传给 nn factory。
 * nn factory 只接尺寸，不反向依赖 process 的 Domain。
 * <p>
 * <b>config 各取所需</b>：本工厂读 nnId + fastMinDt/slowMinDt（urana 节律参数，urana 特定），
 * 忽略 aiId 等上层 key。别的 process 实现读自己需要的 key。
 */
@FunctionalInterface
public interface ProcessFactory {
    /**
     * @param config 配置载体（含各层 mode id + 各层特定参数）。
     * @return 创建好的 IProcessSystem 实例（已注入 nn，可交给上层 ai 工厂）。
     */
    IProcessSystem create(CompoundTag config);
}
