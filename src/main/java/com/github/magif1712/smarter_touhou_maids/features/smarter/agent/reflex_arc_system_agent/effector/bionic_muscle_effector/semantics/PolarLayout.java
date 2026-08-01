package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.bionic_muscle_effector.semantics;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 效应器输入域描述符：256 bits 行为向量 → 肌群拓扑映射。
 * <p>
 * 对标 {@code OutputVectorDomain}（Urana 侧的输出域描述，生产者视角），
 * 本类是消费者视角——效应器如何解读 behavior 子域的内部结构。
 * <p>
 * 极化思想：每肌群 16 bits 重复码（空间容错），关键动作高冗余（冻结位），
 * 连续视角高信息量（信息位）。预留位约定为 0，可作信号质量诊断（冻结位校验）。
 * <p>
 * 设计原则（真善美第 3 条）：把“256 bits 如何解读”这个不实在的概念，
 * 用实在的拓扑描述符固化下来，由效应器按描述切片解码。
 */
public class PolarLayout {

    /** 每个肌群分配的位数（重复码冗余度）。16 bits 多数表决，单 bit 翻转需 >8 才改变判决。 */
    public static final int GROUP_BITS = 16;

    /** 行为向量总位数。 */
    public static final int TOTAL_BITS = 256;

    private final List<MuscleGroupDescriptor> groups;
    private final List<AntagonisticPairDescriptor> pairs;
    private final Span reservedSpan;

    public PolarLayout(List<MuscleGroupDescriptor> groups,
                       List<AntagonisticPairDescriptor> pairs,
                       int reservedOffset, int reservedLength) {
        this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
        this.pairs = Collections.unmodifiableList(new ArrayList<>(pairs));
        this.reservedSpan = new Span(reservedOffset, reservedLength) {};
    }

    public List<MuscleGroupDescriptor> getGroups() {
        return groups;
    }

    public List<AntagonisticPairDescriptor> getPairs() {
        return pairs;
    }

    public Span getReservedSpan() {
        return reservedSpan;
    }

    /**
     * 按身份查肌群描述。
     */
    public MuscleGroupDescriptor getGroup(MuscleGroupId id) {
        for (MuscleGroupDescriptor g : groups) {
            if (g.getId() == id) {
                return g;
            }
        }
        return null;
    }

    /**
     * 默认仿生布局：13 个肌群（每 16 bits）+ 4 个拮抗对 + 48 bits 预留。
     * <pre>
     * offset  id            length  配对
     *   0     FORWARD         16    ↔ BACKWARD
     *  16     BACKWARD        16    ↔ FORWARD
     *  32     STRAFE_LEFT     16    ↔ STRAFE_RIGHT
     *  48     STRAFE_RIGHT    16    ↔ STRAFE_LEFT
     *  64     JUMP            16    —（独立）
     *  80     SNEAK           16    —（独立）
     *  96     ATTACK          16    —（独立）
     * 112     PLACE           16    —（独立）
     * 128     LOOK_UP         16    ↔ LOOK_DOWN
     * 144     LOOK_DOWN       16    ↔ LOOK_UP
     * 160     LOOK_LEFT       16    ↔ LOOK_RIGHT
     * 176     LOOK_RIGHT      16    ↔ LOOK_LEFT
     * 192     HOTBAR          16    —（独立）
     * 208     reserved        48    —（冻结位，约定 0）
     * </pre>
     */
    public static PolarLayout defaultHumanLike() {
        List<MuscleGroupDescriptor> groups = new ArrayList<>();
        int off = 0;
        for (MuscleGroupId id : MuscleGroupId.values()) {
            groups.add(new MuscleGroupDescriptor(id, off, GROUP_BITS));
            off += GROUP_BITS;
        }
        // 13 个肌群 × 16 = 208 bits，预留 48 bits
        int reservedOffset = off;
        int reservedLength = TOTAL_BITS - reservedOffset;

        List<AntagonisticPairDescriptor> pairs = new ArrayList<>();
        pairs.add(new AntagonisticPairDescriptor(MuscleGroupId.FORWARD, MuscleGroupId.BACKWARD));
        pairs.add(new AntagonisticPairDescriptor(MuscleGroupId.STRAFE_LEFT, MuscleGroupId.STRAFE_RIGHT));
        pairs.add(new AntagonisticPairDescriptor(MuscleGroupId.LOOK_UP, MuscleGroupId.LOOK_DOWN));
        pairs.add(new AntagonisticPairDescriptor(MuscleGroupId.LOOK_LEFT, MuscleGroupId.LOOK_RIGHT));

        return new PolarLayout(groups, pairs, reservedOffset, reservedLength);
    }
}
