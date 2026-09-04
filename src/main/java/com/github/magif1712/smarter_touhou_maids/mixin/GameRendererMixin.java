package com.github.magif1712.smarter_touhou_maids.mixin;

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
        // 分支无关守卫（多代理共存，D4 形态修正）：是否需要视觉采集由 SmarterClientService
        // 按自身 agent 初始化且激活判断，不感知任何具体分支的 possession 状态。
        if (!SmarterClientService.INSTANCE.isVisionCaptureNeeded()) return;

        Minecraft mc = Minecraft.getInstance();
        var target = mc.getMainRenderTarget();
        if (target == null) return;

        SmarterClientService.INSTANCE.onPostRender(
                target.getColorTextureId(), target.width, target.height);
    }
}