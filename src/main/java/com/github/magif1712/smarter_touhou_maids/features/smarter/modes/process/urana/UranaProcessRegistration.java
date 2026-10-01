package com.github.magif1712.smarter_touhou_maids.features.smarter.modes.process.urana;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.NnNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.NnNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.bnn_mapper.BnnMapperModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.original_mapper.OriginalMapperModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.process.UranaProcessNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.assembly.ConceptTree;
import com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.assembly.Node;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Process 层分支（urana）的<b>自包含注册</b>（@EventBusSubscriber）。
 * <p>
 * Process 层决定的直接下层插槽（{@link UranaProcessNodeKeys#MAPPER}）由 <b>process 层</b>定义
 * （住 {@code smarter/process/}，W21 归位）。Process 层决定的更下层（per-mapper NN 插槽）由
 * <b>mapper 层</b>定义（{@link NnNodeKeys}，住 {@code smarter/mapper/}）——故 process 层引用
 * mapper 层定义（上→下决定，下→上引用）。
 * <p>
 * 在 {@link FMLCommonSetupEvent} 中（概念树原生注册）：
 * <ol>
 *   <li>创建 {@code MAPPER} 插槽，注册 original_mapper（默认）+ bnn_mapper 两个 Branch
 *       （各自声明 child "nn" 指向自己的专属 NN 插槽——推论2 的结构化裁剪）。</li>
 * </ol>
 * <p>
 * <b>越级装配已归位（R6，W28）</b>：per-mapper NN 插槽（{@code ORIGINAL_MAPPER_NN}/{@code BNN_MAPPER_NN}）
 * 此前由本类创建——那是 process 层<b>隔两层</b>决定 nn 层的插槽（违反"每层只决定其下一层"），
 * 且与同类的 {@code CnnActiveRegistration}（mapper 层自注册）不一致。W28 起改由
 * {@code OriginalMapperRegistration}/{@code BnnMapperRegistration}（mapper 层）创建。
 * 本类<b>只</b>创建自己的直接下层 {@code MAPPER}。
 * <p>
 * <b>时序</b>：本类创建自己的插槽，不依赖其它插槽的存在，故无需特殊 priority。
 * <p>
 * 设计原则（真善美第2条）：process 层只决定 MAPPER（直接下层）。
 */
@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class UranaProcessRegistration {

    private UranaProcessRegistration() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        String modId = SmarterTouhouMaids.MOD_ID;

        // === MAPPER 插槽（process 层的直接下层）===
        Node<FittableMapper> mapper = ConceptTree.builder().node(UranaProcessNodeKeys.MAPPER);
        mapper.addBranch(/* <- */ OriginalMapperModes.mapperBranch(modId));
        mapper.addBranch(/* <- */ BnnMapperModes.mapperBranch(modId));
        mapper.defaultBranch(/* <- */ new ResourceLocation(modId, OriginalMapperModes.MAPPER_ID));
    }
}

