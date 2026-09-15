package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.bnn_mapper.nn.standard_bnn;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapperNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Node;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.RegistryCollectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * standard_bnn（惊跳反射）模块的<b>附属模组式自注册</b>（自包含，@EventBusSubscriber）。
 * <p>
 * <b>为什么经 {@link RegistryCollectEvent} 而不是 {@code FMLCommonSetupEvent}</b>：
 * {@code BNN_MAPPER_NN} 插槽由 process 层在 {@code FMLCommonSetupEvent} 创建（见
 * {@code UranaProcessRegistration}）。本模块要向那个插槽追加 Branch，必须等它存在——
 * {@code RegistryCollectEvent} 正是在「内置注册之后、freeze 之前」这一时窗于 mod 总线 post
 * （时序锁死）。本模块因此<b>不改动任何既有文件</b>（附属形态判据：自包含注册）。
 * <p>
 * <b>注册内容（只有一件事）</b>：向 {@code BNN_MAPPER_NN} 插槽追加 {@code standard_bnn} Branch
 * （与 bnn 同层级、同载体 BoolVector）。<b>不动</b>该插槽的 defaultBranch——默认仍是 bnn，
 * 本模块是<b>选项</b>而非默认（定理1(4)：S/T 同层级；既有约定：附属只 addBranch、不改默认）。
 * <p>
 * 可达性由既有结构保证：bnn_mapper Branch 已声明 {@code child("nn") → BNN_MAPPER_NN}，
 * freeze 的全图可达性校验自然通过。Branch id「standard_bnn」与旧版 NN_LEGACY 插槽中的
 * 同名分支互不冲突（Branch id 唯一性按 Node 隔离校验，见 {@code Node.addBranch}）。
 * <p>
 * <b>可删换</b>（设计原则第3条）：删除本包（含本类）即整体下线，上层零改动。
 * <p>
 * <b>注意</b>：事件处理器里<b>同步</b>执行，不要 enqueueWork（TreeRegistry 非线程安全）。
 * <p>
 * 结构样板：旧版 {@code urana_process_original/.../original_bnn/BnnRegistration}
 * （附属模组式自注册样板）+ 新版 {@code CnnActiveRegistration}（RegistryCollectEvent 时序先例）。
 */
@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class StandardBnnRegistration {

    private StandardBnnRegistration() {
    }

    @SubscribeEvent
    public static void onCollect(RegistryCollectEvent event) {
        // === bnn_mapper 专属 NN 插槽：追加 standard_bnn Branch（不改默认）===
        Node<NnFactory> bnnMapperNn = event.builder().node(FittableMapperNodeKeys.BNN_MAPPER_NN);
        bnnMapperNn.addBranch(StandardBnnModes.nnBranch(SmarterTouhouMaids.MOD_ID));
    }
}
