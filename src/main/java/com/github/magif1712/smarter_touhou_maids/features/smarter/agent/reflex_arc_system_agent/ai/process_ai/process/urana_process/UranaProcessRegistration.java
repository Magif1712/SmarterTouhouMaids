package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.UranaProcessRegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapperFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapperRegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.CnnNnModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.original_mapper.OriginalMapperModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.Registry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Process 层（urana_process，含子包 fittable_mapper/nn）的<b>自包含注册</b>（@EventBusSubscriber）。
 * <p>
 * Process 层自身的 registry id（{@link UranaProcessRegistryIds#MAPPER}）由本层定义。
 * Process 层决定的直接下层 id（{@link FittableMapperRegistryIds#NN}）由 mapper 子层定义——
 * mapper 是 urana_process 的子包，故 process 层引用 mapper 子层定义（上→下决定，下→上引用）。
 * <p>
 * 在 {@link FMLCommonSetupEvent} 中：
 * <ol>
 *   <li>创建 MapperRegistry（id={@link UranaProcessRegistryIds#MAPPER}），注册 urana mapper entry
 *       （经 {@link OriginalMapperModes#mapperEntry(String)} 自包含贡献，subRegistryId=NN）。</li>
 *   <li>创建 NnRegistry（id={@link FittableMapperRegistryIds#NN}），注册 cnn entry
 *       （经 {@link CnnNnModes#nnEntry(String)} 自包含贡献，subRegistryId=null 叶子）。</li>
 * </ol>
 * <p>
 * <b>时序</b>：本类不依赖其它 registry 的存在（创建自己的 registry），故无需特殊 priority。
 * Agent 层（addListener）先于所有 @EventBusSubscriber 触发。
 * <p>
 * 设计原则（真善美第2条）：process 层只决定 MAPPER（直接下层），不定义 NN
 * （NN 由 mapper 子层 {@link FittableMapperRegistryIds} 定义）。
 */
@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class UranaProcessRegistration {

    private UranaProcessRegistration() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        String modId = SmarterTouhouMaids.MOD_ID;

        // === 创建 MapperRegistry（id=MAPPER，由 process 层定义）===
        // urana（核心默认 mapper）自包含注册：id/名/factory 由 OriginalMapperModes 声明。
        // subRegistryId=NN（由 mapper 子层定义）封装在 OriginalMapperModes.mapperEntry 内。
        ResourceLocation mapperDefault = new ResourceLocation(modId, OriginalMapperModes.MAPPER_ID);
        Registry<FittableMapperFactory> mapperRegistry = new Registry<>(UranaProcessRegistryIds.MAPPER, mapperDefault);
        mapperRegistry.register(OriginalMapperModes.mapperEntry(modId));
        RegistryManager.INSTANCE.register(mapperRegistry);

        // === 创建 NnRegistry（id=NN，由 mapper 子层定义）===
        // cnn（核心默认 nn）自包含注册：id/名/factory 由 CnnNnModes 声明。
        // subRegistryId=null（叶子，nn 之下无选择）。
        ResourceLocation nnDefault = new ResourceLocation(modId, CnnNnModes.NN_ID);
        Registry<NnFactory> nnRegistry = new Registry<>(FittableMapperRegistryIds.NN, nnDefault);
        nnRegistry.register(CnnNnModes.nnEntry(modId));
        RegistryManager.INSTANCE.register(nnRegistry);
    }
}
