package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.execution;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.ActionIntent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * 效应器执行端：把 {@link ActionIntent} 落实成 EntityMaid 的实体行为。
 * <p>
 * 生物对应：关节运动 → 世界效应。肌肉张力（ActionIntent）通过“腱”（网络包）
 * 传到执行端，由本类转化为实体的物理运动。跑在服务端（实体权威所在）。
 * <p>
 * 移动向量计算对标项目内 {@code AutoTask} 的成熟写法：
 * forward 向量 = (−sin(yaw), 0, cos(yaw))，strafe 向量 = (cos(yaw), 0, sin(yaw))。
 * 同时调用 {@code getMoveControl().strafe} 与 {@code setDeltaMovement} + hasImpulse，
 * 既更新 AI 移动输入又直接设速度，确保实体立即响应。
 */
public final class MaidActionSink {

    /** 基础移动速度（blocks/tick）。0.25 × 20 = 5 blocks/s，接近玩家行走速度。 */
    private static final float MOVE_SPEED = 0.25f;

    /** 每 tick 视角增量上限（度）。5 × 20 = 100°/s。 */
    private static final float MAX_LOOK_DELTA = 5.0f;

    /**
     * vanilla 跳跃初速度基底（{@code LivingEntity.getJumpPower} 的核心常数 0.42F，
     * 普通方块 {@code blockJumpFactor=1.0} 时的取值）。
     * {@code getJumpPower()} / {@code jumpFromGround()} / {@code getBlockJumpFactor()} 均为 protected，
     * 外部包不可直接调用；对 Forge 模组而言反射按 Mojang 名取方法在 reobfuscation 后会失配（SRG 名），
     * 故此处以常量复刻基底（省略蜂蜜块/灵魂沙的 blockJumpFactor 变化，maid AI 边缘场景）。
     */
    private static final float JUMP_POWER = 0.42f;

    private MaidActionSink() {
    }

    /**
     * 在服务端对 maid 执行一帧操作。
     *
     * @param maid   目标女仆（服务端权威实体）。
     * @param intent 解码后的操作要求。
     */
    public static void execute(EntityMaid maid, ActionIntent intent) {
        applyMovement(maid, intent);
        applyLook(maid, intent);

        if (intent.isJump()) {
            jumpFromGround(maid);
        }
        maid.setShiftKeyDown(intent.isSneak());

        // TODO: 实际攻击/使用物品逻辑（当前仅挥手动画）
        if (intent.isAttack()) {
            maid.swing(InteractionHand.MAIN_HAND);
        }
        if (intent.isPlace()) {
            maid.swing(InteractionHand.OFF_HAND);
        }

        // TODO: hotbar 物品栏切换（待确认 touhoulittlemaid 的背包 API）
    }

    /**
     * 移动：根据朝向把 forward/strafe 转成世界坐标速度。
     * <p>
     * ActionIntent 约定：moveForward 正=前进；moveStrafe 正=左移（STRAFE_LEFT−RIGHT）。
     * Minecraft strafe 约定正=右，故 strafe = −moveStrafe。
     */
    private static void applyMovement(EntityMaid maid, ActionIntent intent) {
        float forward = intent.getMoveForward();
        float strafe = -intent.getMoveStrafe();

        if (forward == 0f && strafe == 0f) {
            maid.getMoveControl().strafe(0.0f, 0.0f);
            maid.setSpeed(0.0f);
            maid.setZza(0.0f);
            maid.setXxa(0.0f);
            maid.setDeltaMovement(0.0, maid.getDeltaMovement().y, 0.0);
            return;
        }

        float yawRad = maid.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forwardVec = new Vec3(-Mth.sin(yawRad), 0, Mth.cos(yawRad));
        Vec3 strafeVec = new Vec3(Mth.cos(yawRad), 0, Mth.sin(yawRad));
        Vec3 delta = forwardVec.scale(forward * MOVE_SPEED)
                .add(strafeVec.scale(strafe * MOVE_SPEED));

        maid.getMoveControl().strafe(strafe, forward);
        // setNoAi(true) 停了 MoveControl.tick()，mob.speed 字段不会被 moveControl 更新（保持 0），
        // 导致 travel 内部 moveRelative(getSpeed()=0, ...) 产生零位移。此处显式 setSpeed 替代之，
        // 让 MaidActionSink 自给自足设全移动字段（speed + xxa/zza），不依赖脊髓反射的 MoveControl。
        maid.setSpeed(MOVE_SPEED);
        maid.setZza(forward);
        maid.setXxa(strafe);
        maid.setDeltaMovement(delta.x, maid.getDeltaMovement().y, delta.z);
        maid.hasImpulse = true;
    }

    /**
     * 让女仆起跳。
     * <p>
     * 复刻 vanilla {@code LivingEntity.jumpFromGround} 的核心路径：
     * jumpY = JUMP_POWER(0.42)（直接覆盖 y，不累加当前 y 速度），再叠加跳跃提升药水效果，
     * 设置 Y 向速度并触发 hasImpulse（省略疾跑前冲分支——maid 无 sprint 起跳语义）。
     * 着地守卫 {@code onGround} 复刻 vanilla {@code aiStep} 的起跳前置条件（onGround && noJumpDelay==0），
     * 避免 intent.isJump() 持续 true 时每 tick 起跳导致持续上升。
     * {@code jumpFromGround()} / {@code getJumpPower()} / {@code getBlockJumpFactor()} 均 protected，
     * 见 {@link #JUMP_POWER} 的说明；此处仅依赖 public 的 {@code onGround}/{@code getEffect}/{@code setDeltaMovement}。
     */
    private static void jumpFromGround(EntityMaid maid) {
        // 空中不跳：复刻 vanilla "着地才能起跳"。MaidActionSink 直接调 jumpFromGround（非经 aiStep
        // 守卫），故自带 onGround 检查，避免 intent.isJump() 持续 true 时每 tick 起跳飞天。
        if (!maid.onGround()) {
            return;
        }
        Vec3 dm = maid.getDeltaMovement();
        // 直接覆盖 y 为 JUMP_POWER——复刻 vanilla jumpFromGround 的 setDeltaMovement(x, f, z)。
        // 原写法 JUMP_POWER + dm.y 会逐 tick 累加 y 速度（每跳 +0.42），导致飞天。
        double jumpY = JUMP_POWER;
        var jumpEffect = maid.getEffect(MobEffects.JUMP);
        if (jumpEffect != null) {
            jumpY += (jumpEffect.getAmplifier() + 1) * 0.1F;
        }
        maid.setDeltaMovement(dm.x, jumpY, dm.z);
        maid.hasImpulse = true;
    }

    /**
     * 视角：把 [-1,1] 增量转成角度增量叠加到当前朝向。pitch clamp 到 [-90,90]。
     */
    private static void applyLook(EntityMaid maid, ActionIntent intent) {
        float newYaw = maid.getYRot() + intent.getLookYawDelta() * MAX_LOOK_DELTA;
        float newPitch = Mth.clamp(
                maid.getXRot() + intent.getLookPitchDelta() * MAX_LOOK_DELTA,
                -90.0f, 90.0f);
        maid.setYRot(newYaw);
        maid.setXRot(newPitch);
        maid.setYHeadRot(newYaw);
        maid.setYBodyRot(newYaw);
    }
}
