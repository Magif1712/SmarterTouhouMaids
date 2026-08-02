package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.AiFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.IAiSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.IProcessSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.ProcessFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.Registry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;

/**
 * 流程型 AI 的 {@link AiFactory} 实现。
 * <p>
 * <b>工厂自驱组装</b>（真善美第1条"真"）：本工厂直接使用 process（ProcessAiSystem 构造注入
 * IProcessSystem），故自行查 {@code ProcessRegistry} 取下层 process factory 创建 process，
 * 再 new ProcessAiSystem(process)。外周不感知 ProcessRegistry 的存在。
 * <p>
 * config 里 processId 缺失/非法时回退 ProcessRegistry 默认 entry（旧存档兼容）。
 * <p>
 * 纯规则 ai 的工厂实现不查 ProcessRegistry，直接造自己的 ai（不依赖本类）。
 */
public class ProcessAiFactory implements AiFactory {

    @Override
    public IAiSystem create(CompoundTag config, EntityMaid maid) {
        // === 查 ProcessRegistry 取下层 process factory（自驱组装）===
        Registry<?> processRegistry = RegistryManager.INSTANCE.get(RegistryIds.PROCESS);
        RegistryEntry<?> processEntry =
                processRegistry.resolve(config.getString(RegistryIds.PROCESS.toString()));
        ProcessFactory processFactory = (ProcessFactory) processEntry.getFactory();

        // 下层 process factory 自驱组装其内部 nn（config + maid 透传，各层各取所需）
        IProcessSystem process = processFactory.create(config, maid);

        // 注入构造（ProcessAiSystem 只直接用 process，不感知 nn）
        return new ProcessAiSystem(process);
    }
}
