package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapperNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.bnn_mapper.BnnMapperModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.bnn_mapper.nn.original_bnn.BnnNnModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.original_mapper.OriginalMapperModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.original_mapper.nn.original_cnn.CnnNnModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.ConceptTree;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Node;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Process 层（urana_process，含子包 fittable_mapper/nn）的<b>自包含注册</b>（@EventBusSubscriber）。
 * <p>
 * Process 层决定的直接下层插槽（{@link UranaProcessNodeKeys#MAPPER}）由本层定义。
 * Process 层决定的更下层（per-mapper NN 插槽）由 mapper 子层
 * {@link FittableMapperNodeKeys} 定义——mapper 是 urana_process 的子包，
 * 故 process 层引用 mapper 子层定义（上→下决定，下→上引用）。
 * <p>
 * 在 {@link FMLCommonSetupEvent} 中（概念树原生注册）：
 * <ol>
 *   <li>创建 MAPPER 插槽，注册 original_mapper（默认）+ bnn_mapper 两个 Branch
 *       （各自声明 child "nn" 指向自己的专属 NN 插槽——推论2 的结构化裁剪）。</li>
 *   <li>创建 original_mapper 专属 NN 插槽（只含 CNN Branch，默认）。</li>
 *   <li>创建 bnn_mapper 专属 NN 插槽（只含 BNN Branch，默认）。</li>
 * </ol>
 * <p>
 * <b>时序</b>：本类创建自己的插槽，不依赖其它插槽的存在，故无需特殊 priority。
 * <p>
 * 设计原则（真善美第2条）：process 层只决定 MAPPER（直接下层），不定义 NN
 * （NN 插槽键由 mapper 子层 {@link FittableMapperNodeKeys} 定义）。
 */
@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class UranaProcessRegistration {

    private UranaProcessRegistration() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        String modId = SmarterTouhouMaids.MOD_ID;

        // === MAPPER 插槽 ===
        Node<FittableMapper> mapper = ConceptTree.builder().node(UranaProcessNodeKeys.MAPPER);
        mapper.addBranch(OriginalMapperModes.mapperBranch(modId));
        mapper.addBranch(BnnMapperModes.mapperBranch(modId));
        mapper.defaultBranch(new ResourceLocation(modId, OriginalMapperModes.MAPPER_ID));

        // === original_mapper 专属 NN 插槽（只含 CNN）===
        Node<NnFactory> originalMapperNn = ConceptTree.builder().node(FittableMapperNodeKeys.ORIGINAL_MAPPER_NN);
        originalMapperNn.addBranch(CnnNnModes.nnBranch(modId));
        originalMapperNn.defaultBranch(new ResourceLocation(modId, CnnNnModes.NN_ID));

        // === bnn_mapper 专属 NN 插槽（只含 BNN）===
        Node<NnFactory> bnnMapperNn = ConceptTree.builder().node(FittableMapperNodeKeys.BNN_MAPPER_NN);
        bnnMapperNn.addBranch(BnnNnModes.nnBranch(modId));
        bnnMapperNn.defaultBranch(new ResourceLocation(modId, BnnNnModes.NN_ID));
    }
}
