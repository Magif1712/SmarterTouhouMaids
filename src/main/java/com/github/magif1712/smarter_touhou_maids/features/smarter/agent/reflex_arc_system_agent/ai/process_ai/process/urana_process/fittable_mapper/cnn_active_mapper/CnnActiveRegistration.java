package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.UranaProcessNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper.nn.cnn_active.CnnActiveNnModes;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Node;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.RegistryCollectEvent;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.TreeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * cnn_active_mapper 模块的概念树注册入口（自包含，@EventBusSubscriber）。
 * <p>
 * <b>为什么经 {@link RegistryCollectEvent} 而不是 {@code FMLCommonSetupEvent}</b>：
 * {@code MAPPER} 插槽由 process 层在 {@code FMLCommonSetupEvent} 创建（见
 * {@code UranaProcessRegistration}）。本模块要在那个插槽上追加 Branch，必须等它存在——
 * {@code RegistryCollectEvent} 正是在"内置注册之后、freeze 之前"这一时窗发布（时序锁死，硬约束6）。
 * 本模块因此<b>不改动 process 层的任何文件</b>（方案 §6.3）。
 * <p>
 * <b>注册两件事</b>：
 * <ol>
 *   <li>向 {@code MAPPER} 追加 {@code cnn_active_mapper} Branch（与 {@code original_mapper}/
 *       {@code bnn_mapper} 同层级）。<b>不动</b>该插槽的 {@code defaultBranch}——默认仍是
 *       {@code original_mapper}，本模块是<b>选项</b>而非默认（定理1(4)：S/T 同层级）。</li>
 *   <li>创建本 mapper 的<b>专属</b> NN 插槽（{@link CnnActiveMapperNodeKeys#CNN_ACTIVE_MAPPER_NN}）
 *       并注册 {@code cnn_active} + 声明默认。用<b>自己的</b>插槽，不碰
 *       {@code ORIGINAL_MAPPER_NN}/{@code BNN_MAPPER_NN}（方案 §十一"defaultBranch 被占"对策）。</li>
 * </ol>
 * 可达性由步骤 1 的 Branch 声明 {@code child("nn")} 指向步骤 2 的插槽保证——
 * freeze 的全图可达性校验因此通过（方案 §十一"freeze 校验失败"对策）。
 * <p>
 * <b>注意</b>：事件处理器里<b>同步</b>执行，不要 enqueueWork（TreeRegistry 非线程安全）。
 */
@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CnnActiveRegistration {

    private CnnActiveRegistration() {
    }

    @SubscribeEvent
    public static void onCollect(RegistryCollectEvent event) {
        String modId = SmarterTouhouMaids.MOD_ID;
        TreeRegistry builder = event.builder();

        // === MAPPER 插槽：追加新 mapper Branch（与 original_mapper / bnn_mapper 同层级）===
        Node<FittableMapper> mapper = builder.node(UranaProcessNodeKeys.MAPPER);
        mapper.addBranch(CnnActiveMapperModes.mapperBranch(modId));

        // === cnn_active_mapper 专属 NN 插槽（只含 cnn_active）===
        Node<NnFactory> cnnActiveMapperNn = builder.node(CnnActiveMapperNodeKeys.CNN_ACTIVE_MAPPER_NN);
        cnnActiveMapperNn.addBranch(CnnActiveNnModes.nnBranch(modId));
        cnnActiveMapperNn.defaultBranch(new ResourceLocation(modId, CnnActiveNnModes.NN_ID));
    }
}
