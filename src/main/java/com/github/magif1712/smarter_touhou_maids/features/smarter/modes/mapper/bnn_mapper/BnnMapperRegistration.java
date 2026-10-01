package com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.bnn_mapper;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.NnNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.original_bnn.BnnNnModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.assembly.ConceptTree;
import com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.assembly.Node;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * bnn_mapper 专属 NN 插槽的 <b>mapper 层自注册</b>（自包含，@EventBusSubscriber）。
 * <p>
 * <b>归属（R6，W28 归位）</b>：{@link NnNodeKeys#BNN_MAPPER_NN} 的 id 由 <b>mapper 层</b>定义（W21），
 * 它是 {@code bnn_mapper} 分支的<b>直接下层</b>——按"每层只决定其下一层"，创建者必须也是 mapper 层。
 * W28 之前它由 {@code urana/UranaProcessRegistration} 隔两层创建（越级装配）。
 * <p>
 * <b>与 {@code StandardBnnRegistration} 的分工</b>：本类创建插槽 + 注册默认分支 {@code bnn}；
 * {@code StandardBnnRegistration} 在 {@code RegistryCollectEvent}（追加窗口）里以"附属模组式"
 * 追加 {@code standard_bnn} 且<b>不改默认</b>——两类的时序由事件边界保证（setup → enqueue → freeze）。
 */
@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BnnMapperRegistration {

    private BnnMapperRegistration() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        String modId = SmarterTouhouMaids.MOD_ID;
        // === bnn_mapper 专属 NN 插槽（只含 BNN；默认 bnn）===
        Node<NnFactory> nn = ConceptTree.builder().node(NnNodeKeys.BNN_MAPPER_NN);
        nn.addBranch(/* <- */ BnnNnModes.nnBranch(modId));
        nn.defaultBranch(/* <- */ new ResourceLocation(modId, BnnNnModes.NN_ID));
    }
}
