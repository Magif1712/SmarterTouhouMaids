package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 效应器输出契约：解码后的操作要求。
 * <p>
 * 客户端效应器（BionicMuscleEffector）产出，服务端执行器（MaidActionSink）消费，
 * 经 {@code ServerboundActionIntentPacket} 在网络间传递——对应生物的“腱”，
 * 把肌肉张力这个不实在的神经信号，转化为实在的数据包传输。
 * <p>
 * 字段设计原则（仿生容错）：
 * <ul>
 *   <li>{@code float [-1,1]}：拮抗对做差后的连续值，保留强度信息。
 *       误码表现为强度微变（0.8→0.75），不是翻转——软判决天然容错。</li>
 *   <li>{@code boolean}：独立肌群经迟滞阈值化（0.6 触发 / 0.4 释放）。
 *       bool 翻转需 float 连续跨越迟滞带，单 bit 误码不足以穿透。</li>
 * </ul>
 * <p>
 * 可变对象，供效应器每 tick 复用（reset 后填充），避免 GC。
 */
public class ActionIntent {

    /** 前后移动意图 [-1,1]：FORWARD 张力 − BACKWARD 张力。正=前进。 */
    private float moveForward;
    /** 左右平移意图 [-1,1]：STRAFE_LEFT − STRAFE_RIGHT。负=右移（对齐 Minecraft strafe 约定：正=右）。 */
    private float moveStrafe;
    /** 俯仰视角增量 [-1,1]：LOOK_UP − LOOK_DOWN。正=抬头。服务端乘系数转角度。 */
    private float lookPitchDelta;
    /** 偏航视角增量 [-1,1]：LOOK_LEFT − LOOK_RIGHT。正=左转。 */
    private float lookYawDelta;
    /** 跳跃（JUMP 张力经迟滞阈值）。 */
    private boolean jump;
    /** 蹲下（SNEAK 张力经迟滞阈值）。 */
    private boolean sneak;
    /** 攻击（ATTACK 张力经迟滞阈值）。 */
    private boolean attack;
    /** 放置/使用（PLACE 张力经迟滞阈值）。 */
    private boolean place;
    /** 物品栏选择 1-9，0=不变。 */
    private int hotbar;

    public ActionIntent() {
        reset();
    }

    /** 重置为“无操作”基线。 */
    public ActionIntent reset() {
        moveForward = 0;
        moveStrafe = 0;
        lookPitchDelta = 0;
        lookYawDelta = 0;
        jump = false;
        sneak = false;
        attack = false;
        place = false;
        hotbar = 0;
        return this;
    }

    public ActionIntent setMoveForward(float v) { this.moveForward = clamp(v); return this; }
    public ActionIntent setMoveStrafe(float v) { this.moveStrafe = clamp(v); return this; }
    public ActionIntent setLookPitchDelta(float v) { this.lookPitchDelta = clamp(v); return this; }
    public ActionIntent setLookYawDelta(float v) { this.lookYawDelta = clamp(v); return this; }
    public ActionIntent setJump(boolean v) { this.jump = v; return this; }
    public ActionIntent setSneak(boolean v) { this.sneak = v; return this; }
    public ActionIntent setAttack(boolean v) { this.attack = v; return this; }
    public ActionIntent setPlace(boolean v) { this.place = v; return this; }
    public ActionIntent setHotbar(int v) { this.hotbar = v; return this; }

    public float getMoveForward() { return moveForward; }
    public float getMoveStrafe() { return moveStrafe; }
    public float getLookPitchDelta() { return lookPitchDelta; }
    public float getLookYawDelta() { return lookYawDelta; }
    public boolean isJump() { return jump; }
    public boolean isSneak() { return sneak; }
    public boolean isAttack() { return attack; }
    public boolean isPlace() { return place; }
    public int getHotbar() { return hotbar; }

    private static float clamp(float v) {
        if (v < -1f) return -1f;
        if (v > 1f) return 1f;
        return v;
    }

    // ===== 网络序列化 =====

    public void writeTo(FriendlyByteBuf buf) {
        buf.writeFloat(moveForward);
        buf.writeFloat(moveStrafe);
        buf.writeFloat(lookPitchDelta);
        buf.writeFloat(lookYawDelta);
        buf.writeBoolean(jump);
        buf.writeBoolean(sneak);
        buf.writeBoolean(attack);
        buf.writeBoolean(place);
        buf.writeVarInt(hotbar);
    }

    public static ActionIntent readFrom(FriendlyByteBuf buf) {
        ActionIntent a = new ActionIntent();
        a.moveForward = buf.readFloat();
        a.moveStrafe = buf.readFloat();
        a.lookPitchDelta = buf.readFloat();
        a.lookYawDelta = buf.readFloat();
        a.jump = buf.readBoolean();
        a.sneak = buf.readBoolean();
        a.attack = buf.readBoolean();
        a.place = buf.readBoolean();
        a.hotbar = buf.readVarInt();
        return a;
    }
}
