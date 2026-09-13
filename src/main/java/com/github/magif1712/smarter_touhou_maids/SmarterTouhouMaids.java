package com.github.magif1712.smarter_touhou_maids;

import com.github.magif1712.smarter_touhou_maids.features.config.ModClientConfig;
import com.github.magif1712.smarter_touhou_maids.features.maid.menu.InitMenus;
import com.github.magif1712.smarter_touhou_maids.features.smarter.possession.ServerPossessionManager;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.AgentDefaults;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.AgentNodeKeys;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterClientService;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.ConceptTree;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.RegistryCollectEvent;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.SmarterTrackingSync;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.SmarterPendingCleanup;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.debug.VisionDebugHook;
import com.github.magif1712.smarter_touhou_maids.network.NetworkHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.HashSet;
import java.util.Set;

@Mod(SmarterTouhouMaids.MOD_ID)
public class SmarterTouhouMaids {
    public static final String MOD_ID = "smarter_touhou_maids";

    public SmarterTouhouMaids(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        InitMenus.MENUS.register(modEventBus);

        // 注册默认模式（agent/sensor/effector 插槽 + 默认分支）。
        // 在 FMLCommonSetupEvent 执行：所有 mod 构造器之后、游戏就绪之前，
        // 附属模组可在自己的 setup event 里经 ConceptTree.builder() 追加注册。
        modEventBus.addListener((FMLCommonSetupEvent e) -> AgentDefaults.registerDefaults());
        // 附属注册收集：内置注册（FMLCommonSetupEvent）之后、freeze（FMLLoadCompleteEvent）之前——
        // 时序锁死（硬约束6）。附属模组 @Mod.EventBusSubscriber(bus=MOD) 监听 RegistryCollectEvent。
        modEventBus.addListener((InterModEnqueueEvent e) ->
                modEventBus.post(new RegistryCollectEvent(ConceptTree.builder())));
        // 概念树 freeze：全部 setup 注册完成后做全图校验（类型/环/可达/契约/全默认可满足），
        // 生成运行期只读快照。多根：agent 域 + config_gui 域（后者仅客户端注册）。
        modEventBus.addListener((FMLLoadCompleteEvent e) -> {
            Set<ResourceLocation> roots = new HashSet<>();
            roots.add(AgentNodeKeys.AGENT.id());
            ResourceLocation configGuiId = new ResourceLocation(MOD_ID, "config_gui");
            if (ConceptTree.builder().get(configGuiId) != null) {
                roots.add(configGuiId);
            }
            ConceptTree.freeze(roots /*->*/);
        });

        MinecraftForge.EVENT_BUS.register(ServerPossessionManager.INSTANCE);
        MinecraftForge.EVENT_BUS.register(SmarterClientService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new SmarterTrackingSync());
        MinecraftForge.EVENT_BUS.register(new SmarterPendingCleanup());
        ///////////////debug/////////////
        // 视觉调试钩子（合并后单一代理分支，只注册新版——其双载体分派覆盖旧 BNN 位平面行为）。
        MinecraftForge.EVENT_BUS.register(VisionDebugHook.INSTANCE);
        /////////////debug end///////////
        NetworkHandler.init();

        ModClientConfig.register();
    }
}