package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.sensor.possession_sensor.possession.client.setup;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterClientService;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.sensor.possession_sensor.possession.core.PossessionManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 将 possession 的 maid 来源（getPossessedMaid）注入 smarter 通用层（SmarterClientService）。
 * <p>
 * 依赖方向：reflex_arc（下层）→ smarter/agent（上层抽象 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.MaidSource}），
 * 而非通用层反向依赖具体实现（真善美第3条：上层依赖抽象，下层提供实现并主动注入）。
 * <p>
 * 多代理共存（D4 形态修正）：各分支各注入一份（addMaidSource），服务层遍历取第一个非 null——
 * 同一时刻玩家只附身一个 maid，只有持有该 maid 的分支来源返回非 null。
 * 换非附身 agent 时，新的 sensor 子系统提供自己的 MaidSource 实现在各自 client setup 注入，
 * SmarterClientService 零改动。
 */
@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MaidSourceBinding {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SmarterClientService.INSTANCE.addMaidSource(
                () -> PossessionManager.INSTANCE.getPossessedMaid());
    }
}
