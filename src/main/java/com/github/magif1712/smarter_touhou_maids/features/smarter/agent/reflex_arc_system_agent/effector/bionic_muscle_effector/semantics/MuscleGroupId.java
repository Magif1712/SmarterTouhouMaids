package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.bionic_muscle_effector.semantics;

/**
 * 肌群身份标识。
 * <p>
 * 绑定“动作意图”而非“按键”——无论未来是按键控制、实体控制还是其他方式，
 * 肌群身份都是“效应器要做什么”的语义，不随实现方式改变。
 * <p>
 * 按拮抗对分组排列：相邻两个互为拮抗（如 FORWARD/BACKWARD），
 * 便于 {@link PolarLayout} 构造时配对。
 */
public enum MuscleGroupId {
    // === 移动拮抗对 ===
    FORWARD, BACKWARD,
    STRAFE_LEFT, STRAFE_RIGHT,

    // === 视角拮抗对（连续量）===
    LOOK_UP, LOOK_DOWN,
    LOOK_LEFT, LOOK_RIGHT,

    // === 独立肌群（无拮抗对）===
    JUMP,
    SNEAK,
    ATTACK,
    PLACE,
    HOTBAR
}
