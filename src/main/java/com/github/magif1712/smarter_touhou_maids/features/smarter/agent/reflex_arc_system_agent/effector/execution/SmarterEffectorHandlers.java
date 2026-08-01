package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.execution;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 效应器服务端事件钩子：maid 加入世界时清零方案A 残留的 {@code NoAi} 标志。
 * <p>
 * 方案A（已废弃）曾用 {@code setNoAi(true)} 抑制脊髓反射，NoAi 随 NBT 持久化。
 * 回退到方案B（mixin cancel serverAiStep）后不再需要 NoAi，但旧存档的 maid 可能仍带
 * {@code NoAi=true}——若不清零，{@code isEffectiveAi()=false} 会让 {@code travel()} 被跳过，
 * maid 无法移动。此钩子在 maid 加入世界时无条件 {@code setNoAi(false)} 清除该残留。
 * <p>
 * 脊髓反射的抑制现由 {@link com.github.magif1712.smarter_touhou_maids.mixin.MobServerAiStepSuppressMixin}
 * 在 {@code serverAiStep} HEAD cancel 完成，与 NoAi 标志无关。
 * <p>
 * 设计原则（真善美第 3 条）：把"应清零方案A残留 NoAi"这个不实在的约束，
 * 用实在的 EntityJoinLevelEvent 校正固化。
 */
@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SmarterEffectorHandlers {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        // 无条件清零 NoAi：方案B 不再依赖 NoAi 抑制脊髓反射（改由 mixin cancel serverAiStep）。
        // 此处仅清除方案A 残留，确保 isEffectiveAi()=true → travel() 正常运转。
        // 幂等——NoAi 已为 false 时无副作用。
        maid.setNoAi(false);
    }
}
