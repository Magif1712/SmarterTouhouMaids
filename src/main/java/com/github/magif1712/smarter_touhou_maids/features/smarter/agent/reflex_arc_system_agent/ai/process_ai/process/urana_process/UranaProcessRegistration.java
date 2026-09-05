package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.UranaProcessRegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapperFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapperRegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.bnn_mapper.BnnMapperModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.bnn_mapper.nn.original_bnn.BnnNnModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.original_mapper.nn.original_cnn.CnnNnModes;
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
 * Process 层决定的直接下层 id（{@link FittableMapperRegistryIds#ORIGINAL_MAPPER_NN} /
 * {@link FittableMapperRegistryIds#BNN_MAPPER_NN}）由 mapper 子层定义——
 * mapper 是 urana_process 的子包，故 process 层引用 mapper 子层定义（上→下决定，下→上引用）。
 * <p>
 * 在 {@link FMLCommonSetupEvent} 中：
 * <ol>
 *   <li>创建 MapperRegistry（id={@link UranaProcessRegistryIds#MAPPER}），注册 urana mapper entry
 *       （经 {@link OriginalMapperModes#mapperEntry(String)} 自包含贡献，
 *       subRegistryId=ORIGINAL_MAPPER_NN）。</li>
 *   <li>创建 original_mapper 专属 NnRegistry（id={@link FittableMapperRegistryIds#ORIGINAL_MAPPER_NN}），
 *       只注册 CNN entry（经 {@link CnnNnModes#nnEntry(String)} 自包含贡献，subRegistryId=null 叶子）。</li>
 *   <li>创建 bnn_mapper 专属 NnRegistry（id={@link FittableMapperRegistryIds#BNN_MAPPER_NN}），
 *       只注册 BNN entry（经 {@link BnnNnModes#nnEntry(String)} 自包含贡献，subRegistryId=null 叶子）。</li>
 * </ol>
 * <p>
 * <b>per-mapper NN registry</b>（设计原则2）：NN 是 mapper 的模式，不同 mapper 外延只能看到
 * 与自己载体兼容的 NN 子集。original_mapper（FloatVector）只能配 CNN；bnn_mapper（BoolVector）
 * 只能配 BNN。每个 mapper entry 指向自己的 NN registry，GUI 递归时 {@code registry.getAllIds()}
 * 自然只返回该 mapper 兼容的 NN 选项——切换 mapper 时 NN 选项自动切换，上层（process）零改动。
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
        // subRegistryId=ORIGINAL_MAPPER_NN（original_mapper 的专属 NN registry）封装在 OriginalMapperModes.mapperEntry 内。
        ResourceLocation mapperDefault = new ResourceLocation(modId, OriginalMapperModes.MAPPER_ID);
        Registry<FittableMapperFactory> mapperRegistry = new Registry<>(UranaProcessRegistryIds.MAPPER, mapperDefault);
        mapperRegistry.register(OriginalMapperModes.mapperEntry(modId));
        // bnn_mapper（BNN 载体，BoolVector）作为可选项注册——默认仍是 original_mapper，
        // 测试时在 GUI 切换 mapper 到 bnn_mapper（NN 自动切换到 BNN）验证 GPU 占用假说。
        // subRegistryId=BNN_MAPPER_NN（bnn_mapper 的专属 NN registry）封装在 BnnMapperModes.mapperEntry 内。
        mapperRegistry.register(BnnMapperModes.mapperEntry(modId));
        RegistryManager.INSTANCE.register(mapperRegistry);

        // === 创建 original_mapper 专属 NnRegistry（id=ORIGINAL_MAPPER_NN，只含 CNN）===
        // per-mapper NN registry（设计原则2）：original_mapper 的 FloatVector 载体只兼容 CNN，
        // GUI 选了 original_mapper 后 NN 层只显示 CNN 一个选项。
        ResourceLocation cnnDefault = new ResourceLocation(modId, CnnNnModes.NN_ID);
        Registry<NnFactory> originalMapperNnRegistry = new Registry<>(FittableMapperRegistryIds.ORIGINAL_MAPPER_NN, cnnDefault);
        originalMapperNnRegistry.register(CnnNnModes.nnEntry(modId));
        RegistryManager.INSTANCE.register(originalMapperNnRegistry);

        // === 创建 bnn_mapper 专属 NnRegistry（id=BNN_MAPPER_NN，只含 BNN）===
        // per-mapper NN registry（设计原则2）：bnn_mapper 的 BoolVector 载体只兼容 BNN，
        // GUI 选了 bnn_mapper 后 NN 层只显示 BNN 一个选项。
        ResourceLocation bnnDefault = new ResourceLocation(modId, BnnNnModes.NN_ID);
        Registry<NnFactory> bnnMapperNnRegistry = new Registry<>(FittableMapperRegistryIds.BNN_MAPPER_NN, bnnDefault);
        bnnMapperNnRegistry.register(BnnNnModes.nnEntry(modId));
        RegistryManager.INSTANCE.register(bnnMapperNnRegistry);
    }
}