package com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.original_mapper;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.NnNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.original_cnn.CnnNnModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.assembly.ConceptTree;
import com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.assembly.Node;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * original_mapper 专属 NN 插槽的 <b>mapper 层自注册</b>（自包含，@EventBusSubscriber）。
 * <p>
 * <b>归属（R6，W28 归位）</b>：{@link NnNodeKeys#ORIGINAL_MAPPER_NN} 的 id 由 <b>mapper 层</b>定义
 * （住 {@code smarter/mapper/}，W21），它是 {@code original_mapper} 分支的<b>直接下层</b>——
 * 按"每层只决定其下一层"，创建者必须也是 <b>mapper 层</b>。
 * <p>
 * W28 之前本插槽由 {@code urana/UranaProcessRegistration}（process 层的分支实现）<b>隔两层</b>创建，
 * 而同类的 {@code CnnActiveRegistration}（mapper 层）创建自己的 NN 插槽——同一件事两种层级，
 * 是"每层只决定其下一层"的代码级违反。归位后两者形态一致：<b>谁的分支，谁创建并声明自己的 NN 子插槽</b>。
 * <p>
 * <b>时序</b>：本类只创建 mapper 层自己的插槽（不依赖 MAPPER 插槽存在），故走
 * {@link FMLCommonSetupEvent} 默认优先级即可——与 {@code UranaProcessRegistration} 同期，
 * 早于 {@code SmarterTouhouMaids} 发布的 {@code RegistryCollectEvent}（附属/追加窗口）与 {@code freeze}
 * （时序锁死，硬约束6）。向本插槽<b>追加</b>分支的模块（如 {@code StandardBnnRegistration}）走
 * {@code RegistryCollectEvent}。
 */
@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class OriginalMapperRegistration {

    private OriginalMapperRegistration() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        String modId = SmarterTouhouMaids.MOD_ID;
        // === original_mapper 专属 NN 插槽（只含 CNN；默认 cnn）===
        Node<NnFactory> nn = ConceptTree.builder().node(NnNodeKeys.ORIGINAL_MAPPER_NN);
        nn.addBranch(/* <- */ CnnNnModes.nnBranch(modId));
        nn.defaultBranch(/* <- */ new ResourceLocation(modId, CnnNnModes.NN_ID));
    }
}
