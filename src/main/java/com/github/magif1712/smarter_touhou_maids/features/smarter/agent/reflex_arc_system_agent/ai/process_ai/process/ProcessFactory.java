package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.INeuralNetwork;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
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
 * <b>config 各取所需</b>：本工厂读 nnId，忽略 aiId 等上层 key。别的 process 实现读自己需要的 key。
 * <p>
 * <b>per-maid 参数各取所需</b>（真善美第3条）：本工厂读自己声明的 per-maid 参数（如 urana 的快/慢环
 * minDt），经 {@code ParamStore} 查 maid，nbtKey 由本工厂自备。外周不再硬编码这些 key 进 config——
 * 换 process 时新 factory 自带自己的参数 key，外周与上层工厂零改动。
 * <p>
 * <b>slot 透传 + load</b>（C3 时机对称）：slot 透传给 nn factory 供其 load 权重；本工厂在创建
 * process 实例后调 process.load(slot) 加载自身状态（∇C/继承/时间）。纯规则 process 不解读 slot。
 */
@FunctionalInterface
public interface ProcessFactory {
    /**
     * @param config 配置载体（含各层 mode id）。
     * @param maid   目标女仆（供本 factory 经 ParamStore 读自己声明的 per-maid 参数）。
     * @param slot   持久化槽位（透传给 nn factory load 权重；本 factory 用其 load process 状态）。
     * @return 创建好的 IProcessSystem 实例（已注入 nn 且已 load，可交给上层 ai 工厂）。
     */
    IProcessSystem create(CompoundTag config, EntityMaid maid, SaveSlot slot);
}
