package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.AiFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.IAiSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.IProcessSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.ProcessFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.ProcessAiRegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.Registry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * 流程型 AI 的 {@link AiFactory} 实现。
 * <p>
 * <b>工厂自驱组装</b>（真善美第1条"真"）：本工厂直接使用 process（ProcessAiSystem 构造注入
 * IProcessSystem），故自行查 {@code ProcessRegistry} 取下层 process factory 创建 process，
 * 再 new ProcessAiSystem(process)。外周不感知 ProcessRegistry 的存在。
 * <p>
 * <b>注入 process registry id</b>（注册链分叉后必要）：新版代理（AI_SMARTER）与原初代理（AI）
 * 各有独立的 PROCESS registry（PROCESS_SMARTER / PROCESS），只含兼容的流程 entry。
 * ProcessAiFactory 由注册层（ProcessAiRegistration）构造注入 processRegistryId——
 * 知道查哪个 PROCESS registry，无需从 config 推断（真善美第4条：不实在的"哪个 registry"
 * 用实在的构造注入固化）。
 * <p>
 * config 里 processId 缺失/非法时回退 ProcessRegistry 默认 entry（旧存档兼容）。
 * <p>
 * 纯规则 ai 的工厂实现不查 ProcessRegistry，直接造自己的 ai（不依赖本类）。
 */
public class ProcessAiFactory implements AiFactory {

    /** 注入的 PROCESS registry id（决定查哪个 process registry，由注册层注入）。 */
    private final ResourceLocation processRegistryId;

    /** 默认构造（向后兼容，查旧 PROCESS registry——原初代理路径）。 */
    public ProcessAiFactory() {
        this(ProcessAiRegistryIds.PROCESS);
    }

    /** 注入构造（注册层用——新版代理注入 PROCESS_SMARTER，原初代理注入 PROCESS）。 */
    public ProcessAiFactory(ResourceLocation processRegistryId) {
        this.processRegistryId = processRegistryId;
    }

    @Override
    public IAiSystem create(CompoundTag config, EntityMaid maid, SaveSlot slot) {
        // === 查 ProcessRegistry 取下层 process factory（自驱组装）===
        // 查注入的 processRegistryId 对应的 registry（新版代理→PROCESS_SMARTER 只含 urana；
        // 原初代理→PROCESS 只含 urana_original），非法组合结构上不可选。
        Registry<?> processRegistry = RegistryManager.INSTANCE.get(processRegistryId);
        RegistryEntry<?> processEntry =
                processRegistry.resolve(config.getString(processRegistryId.toString()));
        ProcessFactory processFactory = (ProcessFactory) processEntry.getFactory();

        // 下层 process factory 自驱组装其内部 nn（config + maid + slot 透传，各层各取所需）
        IProcessSystem process = processFactory.create(config, maid, slot);

        // 注入构造（ProcessAiSystem 只直接用 process，不感知 nn）
        return new ProcessAiSystem(process);
    }
}
