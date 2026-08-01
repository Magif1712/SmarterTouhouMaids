package com.github.magif1712.smarter_touhou_maids.mixin;

import com.github.magif1712.smarter_touhou_maids.features.smarter.state.MaidSmarterState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 抑制原版 AI 的 {@code Mob.serverAiStep}（脊髓反射），让 smarter 激活时
 * {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.execution.MaidActionSink}
 * （意识→肌肉）独占实体控制权。
 * <p>
 * {@code serverAiStep} 内含 sensing / targetSelector / goalSelector / navigation /
 * {@code customServerAiStep}（TLM Brain 在此运转）/ moveControl / lookControl / jumpControl。
 * 在 smarter 激活时整体 cancel，等价于"脊髓反射被意识抑制"，但<b>不</b>动 {@code isNoAi()} 标志。
 * <p>
 * <b>激活状态来源</b>：{@link MaidSmarterState#isEnabled} 现语义为"agent 激活状态"
 * （原"用户 UI 开关"，已变迁）。由客户端 {@code SmarterClientService} 检测
 * {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.IAgent#isActive()}
 * 边界变化时 sync 写入（经 ServerboundSetSmarterModePacket）。
 * <p>
 * 设计原则（真善美第 3 条）：本 mixin（上层）只读 sync 后的激活标量，不依赖下层激活条件
 * （附身/自动任务/agent 类型等）。换 agent（激活条件从附身变为别的）时，本 mixin 零改动——
 * agent 自决 isActive + 客户端 sync 即可。把"脊髓反射是否被抑制"这个不实在的状态，
 * 用实在的 mixin cancel + 标量读取固化。
 * <p>
 * Mixin 目标是 {@code Mob.class}（{@code serverAiStep} 声明所在，且为 {@code final} 不可被子类覆盖），
 * 用 {@code instanceof EntityMaid} 守卫，避免影响其它 mob。
 */
@Mixin(Mob.class)
public abstract class MobServerAiStepSuppressMixin {

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void smarter_touhou_maids$suppressServerAiStepWhenSmarter(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (self instanceof EntityMaid maid && MaidSmarterState.isEnabled(maid)) {
            // smarter 开启：cancel 整个 serverAiStep = 抑制 brain + goalSelector + controls，
            // 但 isNoAi 标志不动，isEffectiveAi() 仍为 true，travel() 正常运行。
            ci.cancel();
        }
    }
}
