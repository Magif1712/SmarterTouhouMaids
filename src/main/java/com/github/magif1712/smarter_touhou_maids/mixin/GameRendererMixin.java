package com.github.magif1712.smarter_touhou_maids.mixin;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.core.PossessionManager;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterClientService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void onPostRender(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        if (!renderLevel) return;
        if (!PossessionManager.INSTANCE.isPossessing()) return;

        Minecraft mc = Minecraft.getInstance();
        var target = mc.getMainRenderTarget();
        if (target == null) return;

        SmarterClientService.INSTANCE.onPostRender(
                target.getColorTextureId(), target.width, target.height);
    }
}