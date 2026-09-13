package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.original_bnn;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.LegacyNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.ConceptTree;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Node;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 原初 bnn 的<b>附属模组式自注册</b>（也是附属模组注册新 nn 模式的样板）。
 * <p>
 * 在 {@link FMLCommonSetupEvent} 向 NN_LEGACY 插槽追加 original_bnn Branch——
 * 与真正的附属模组注册路径完全一致。
 * <p>
 * <b>时序</b>：NN_LEGACY 插槽由旧版层 {@code UranaProcessRegistration}（LOWEST）创建。
 * 概念树的 {@code TreeRegistry.node()} 是"创建或取回"——本类先跑也会先创建插槽，
 * standard_bnn 与默认分支随后由 UranaProcessRegistration 补齐，两种顺序均正确。
 * <p>
 * <b>附属模组样板</b>：附属模组复制本类结构（改 modid / 改 Branch 来源）即可注册自己的
 * nn 模式，主模组无需任何改动。
 */
@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BnnRegistration {

    private BnnRegistration() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        Node<NnFactory> nnLegacy = ConceptTree.builder().node(LegacyNodeKeys.NN_LEGACY);
        nnLegacy.addBranch(BnnModes.nnBranch(SmarterTouhouMaids.MOD_ID));
    }
}
