package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.AiFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.IAiSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.IProcessSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.AssemblyContext;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.OutSlot;

/**
 * 流程型 AI 的 {@link AiFactory} 实现（概念树重构后）。
 * <p>
 * <b>工厂自驱组装</b>（真善美第1条"真"）：本工厂直接使用 process（ProcessAiSystem 构造注入
 * IProcessSystem），process 子实例由 Resolver 按本分支声明的 child（"process"）解析传入。
 * 本工厂不感知 process 之下有什么（mapper/nn）——「我的附庸的附庸不是我的附庸」。
 * <p>
 * 纯规则 ai 的工厂不声明 process child，直接造自己的 ai（不依赖本类）。
 */
public class ProcessAiFactory implements AiFactory {

    @Override
    public void create(AssemblyContext ctx, /*->*/ OutSlot<IAiSystem> out) {
        // process 子实例由 Resolver 解析（含 mapper/nn 的全套自驱组装）
        IProcessSystem process = ctx.child("process", IProcessSystem.class);
        // 注入构造（ProcessAiSystem 只直接用 process，不感知 nn）
        out.set(new ProcessAiSystem(process));
    }
}
