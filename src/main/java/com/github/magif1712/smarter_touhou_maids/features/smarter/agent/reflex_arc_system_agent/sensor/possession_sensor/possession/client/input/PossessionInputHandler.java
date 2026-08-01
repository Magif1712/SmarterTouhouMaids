package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.client.input;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.client.setup.KeysRegistry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.core.PossessionManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;

@Mod.EventBusSubscriber(modid = SmarterTouhouMaids.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PossessionInputHandler {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!KeysRegistry.POSSESSION_KEY.consumeClick()) return;

        if (PossessionManager.INSTANCE.isPossessing()) {
            PossessionManager.INSTANCE.requestStopPossession();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        EntityMaid target = null;

        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) {
            Entity e = ((EntityHitResult) mc.hitResult).getEntity();
            if (e instanceof EntityMaid) target = (EntityMaid) e;
        }

        if (target == null && mc.player != null) {
            target = mc.player.level().getEntitiesOfClass(
                    EntityMaid.class,
                    mc.player.getBoundingBox().inflate(4.0),
                    maid -> maid.isAlive() && PossessionManager.INSTANCE.isPossessionEnabled(maid)
            ).stream().min(Comparator.comparingDouble(
                    maid -> maid.distanceToSqr(mc.player)
            )).orElse(null);
        }

        if (target != null && PossessionManager.INSTANCE.isPossessionEnabled(target)) {
            PossessionManager.INSTANCE.requestPossession(target);
        }
    }
}