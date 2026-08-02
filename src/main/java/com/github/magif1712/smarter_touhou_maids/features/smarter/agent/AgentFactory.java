package com.github.magif1712.smarter_touhou_maids.features.smarter.agent;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;

/**
 * Agent 顶层工厂：按配置创建一个 {@link IAgent} 实例。
 * <p>
 * <b>工厂自驱组装</b>（真善美第1条"真"：每层 factory 只注入自己直接使用的下层抽象）：
 * 本工厂直接使用 ai（ReflexArcSystemAgent 构造注入 IAiSystem），故自行查
 * {@code AiRegistry} 取下层 ai factory 并创建 ai，再 new ReflexArcSystemAgent(ai)。
 * 外周（SmarterClientService）不感知下层 registry 的存在——它只调本工厂。
 * <p>
 * <b>config 各取所需</b>：config 是一个 CompoundTag，含所有层的选择 id
 * （key = registryId.toString()）。本工厂只读自己需要的 key（aiId），忽略其余。
 * <p>
 * <b>maid 各取所需</b>（真善美第3条）：maid 是 per-maid 状态的统一入口。各层 factory 需要读
 * per-maid 参数（如 process 层读 urana 节律参数）时，经 maid 查 {@code ParamStore}，nbtKey 由该层自备。
 * 外周（SmarterClientService）不再硬编码下层参数 key 进 config——换 process 时外周零改动。
 * 与 config 对称：config 透传，各层读自己需要的 key；maid 透传，各层读自己需要的参数。
 * <p>
 * 附属 agent 工厂实现可不查 AiRegistry（如纯规则 agent 不需要 ai），直接 new 自己的 agent。
 */
@FunctionalInterface
public interface AgentFactory {
    /**
     * @param config 配置载体（含各层 mode id），各 factory 各取所需。
     * @param maid   目标女仆（per-maid 状态入口，供下层 factory 经 ParamStore 读自己声明的参数）。
     * @return 创建好的 IAgent 实例（已注入下层 ai，但尚未 awaken——awaken 由 SmarterClientService 调用）。
     */
    IAgent create(CompoundTag config, EntityMaid maid);
}
