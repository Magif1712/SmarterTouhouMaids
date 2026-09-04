package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.effector.bionic_muscle_effector.muscle;

/**
 * 拮抗肌对：两个肌群张力做差，输出关节力矩。
 * <p>
 * 生物对应：屈肌/伸肌成对出现，脊髓层面侧抑制——主肌兴奋会抑制拮抗肌，
 * 实际输出为两者之差。“共同收缩”自动抵消——这是对称容错。
 * <p>
 * 误码作用：若整个 behavior 向量有共模偏移（训练噪声让所有 bit 偏高），
 * 两个肌群同时偏高，做差后共模噪声完全抵消。对应极化码的极化变换——
 * 差分消除共模误差，把“绝对值误差”转成“差值误差”，信息更聚焦。
 * <p>
 * 无状态：仅持两个 {@link MuscleGroup} 引用，做差是即时运算。
 * 调用顺序：先两个肌群 {@link MuscleGroup#tick(int[])} 更新张力，再 {@link #resolve()} 做差。
 */
public class AntagonisticPair {

    private final MuscleGroup agonist;
    private final MuscleGroup antagonist;

    public AntagonisticPair(MuscleGroup agonist, MuscleGroup antagonist) {
        this.agonist = agonist;
        this.antagonist = antagonist;
    }

    /**
     * 主肌张力 − 拮抗肌张力，输出 [-1,1]。
     * <ul>
     *   <li>正：主肌占优（如 FORWARD > BACKWARD → 前进）</li>
     *   <li>负：拮抗肌占优（如 BACKWARD > FORWARD → 后退）</li>
     *   <li>0：共同收缩或共同静息，不产生关节运动</li>
     * </ul>
     */
    public float resolve() {
        return agonist.getTension() - antagonist.getTension();
    }

    public MuscleGroup getAgonist() {
        return agonist;
    }

    public MuscleGroup getAntagonist() {
        return antagonist;
    }
}
