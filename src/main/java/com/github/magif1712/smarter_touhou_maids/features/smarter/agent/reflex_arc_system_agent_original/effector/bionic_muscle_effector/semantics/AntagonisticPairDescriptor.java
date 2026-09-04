package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.effector.bionic_muscle_effector.semantics;

/**
 * 拮抗对描述符：主肌（agonist）与拮抗肌（antagonist）的配对关系。
 * <p>
 * 生物对应：屈肌/伸肌成对出现，脊髓层面侧抑制——主肌兴奋会抑制拮抗肌，
 * 实际输出为两者之差。“共同收缩”自动抵消，是一种天然的纠错码（消除无效码字）。
 * <p>
 * 只持两个 {@link MuscleGroupId}（不持 Descriptor），耦合更低——
 * 通过 id 可在 {@link PolarLayout#getGroup(MuscleGroupId)} 查到完整描述。
 * 关系是肌群的“模式”，应在肌群的下层，不混入肌群描述本身。
 */
public class AntagonisticPairDescriptor {

    private final MuscleGroupId agonist;
    private final MuscleGroupId antagonist;

    public AntagonisticPairDescriptor(MuscleGroupId agonist, MuscleGroupId antagonist) {
        this.agonist = agonist;
        this.antagonist = antagonist;
    }

    public MuscleGroupId getAgonist() {
        return agonist;
    }

    public MuscleGroupId getAntagonist() {
        return antagonist;
    }
}
