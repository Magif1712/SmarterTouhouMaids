package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.effector.bionic_muscle_effector;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.ActionIntent;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.IEffector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.effector.bionic_muscle_effector.muscle.AntagonisticPair;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.effector.bionic_muscle_effector.muscle.MuscleGroup;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.effector.bionic_muscle_effector.semantics.AntagonisticPairDescriptor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.effector.bionic_muscle_effector.semantics.MuscleGroupDescriptor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.effector.bionic_muscle_effector.semantics.MuscleGroupId;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.effector.bionic_muscle_effector.semantics.PolarLayout;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * 仿生肌肉效应器：唯一的具体 {@link IEffector} 实现，bit-packed 行为向量 → {@link ActionIntent}。
 * <p>
 * 对标 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.sensor.possession_sensor.PossessionSensor}
 * （感受器入口），把不实在的 256 bits 神经信号，用实在的肌肉力学链转化为实在的操作要求。编排三步：
 * <ol>
 *   <li>所有肌群 {@link MuscleGroup#tick} —— 运动单元池加权求和（空间容错）
 *       + {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.effector.bionic_muscle_effector.muscle.TensionIntegrator}
 *       低通滤波（时间容错）</li>
 *   <li>拮抗对 {@link AntagonisticPair#resolve} 做差（对称容错）→ float 字段</li>
 *   <li>独立肌群迟滞阈值化（判决层容错）→ boolean 字段</li>
 * </ol>
 * <p>
 * 四层容错叠加：单 bit 翻转需穿透"16 bits 多数 + 拮抗差值 + 时间平滑 + 迟滞带"
 * 才改变 ActionIntent。误码率从 bit 级 ~20% 压缩到最终动作级 ~0.24%。
 * <p>
 * 有状态：持有每肌群的张力积分器与迟滞触发状态，对应"肌肉的记忆"。
 * tick 频率 20Hz（客户端 tick），Urana 5Hz 更新 behavior，频率差提供时间容错主体。
 * <p>
 * <b>生命周期</b>（实现 {@link IEffector}）：构造只存 layout + behaviorSize（轻量）；
 * {@link #awaken} 初始化肌群/拮抗对/迟滞状态/复用 intent；{@link #tick} 解码；{@link #shutdown} 复位。
 */
public class BionicMuscleEffector implements IEffector {

    /** 张力积分系数 α=0.5（tick=50ms, τ=50ms → 人肌肉时间常数）。 */
    private static final float ALPHA = 0.5f;

    /** 迟滞阈值：张力 > HIGH 触发，< LOW 释放，中间保持。施密特触发器抗边界抖动。 */
    private static final float HYSTERESIS_HIGH = 0.6f;
    private static final float HYSTERESIS_LOW = 0.4f;

    private final PolarLayout layout;
    private final int behaviorSize;

    /** 肌群（awaken 后初始化）。 */
    private EnumMap<MuscleGroupId, MuscleGroup> groups;
    /** 拮抗对（awaken 后初始化）。 */
    private List<AntagonisticPair> pairs;
    /** 独立肌群迟滞触发状态（awaken 后初始化）。 */
    private EnumMap<MuscleGroupId, Boolean> hysteresisState;
    /** 复用的操作要求实例（awaken 后初始化，每 tick reset 后填充，避免 GC）。 */
    private ActionIntent intent;

    /**
     * @param layout       极化布局（肌群拓扑描述）。
     * @param behaviorSize 行为向量位宽（由 factory 校验 == 256）。
     */
    public BionicMuscleEffector(PolarLayout layout, int behaviorSize) {
        this.layout = layout;
        this.behaviorSize = behaviorSize;
    }

    @Override
    public void awaken() {
        this.groups = new EnumMap<>(MuscleGroupId.class);
        for (MuscleGroupDescriptor desc : layout.getGroups()) {
            groups.put(desc.getId(), new MuscleGroup(desc, ALPHA));
        }
        this.pairs = new ArrayList<>();
        for (AntagonisticPairDescriptor pd : layout.getPairs()) {
            MuscleGroup ag = groups.get(pd.getAgonist());
            MuscleGroup ant = groups.get(pd.getAntagonist());
            this.pairs.add(new AntagonisticPair(ag, ant));
        }
        this.hysteresisState = new EnumMap<>(MuscleGroupId.class);
        for (MuscleGroupId id : MuscleGroupId.values()) {
            hysteresisState.put(id, Boolean.FALSE);
        }
        this.intent = new ActionIntent();
    }

    /**
     * 解码一帧行为向量，输出操作要求。
     * <p>
     * 返回的 {@link ActionIntent} 是复用实例，下次 tick 后失效。调用方应立即消费
     * （如序列化发包），不要跨 tick 持有引用。
     */
    @Override
    public ActionIntent tick(int[] packedBehavior) {
        intent.reset();

        // 1. 运动单元池加权求和 + 张力积分（空间 + 时间容错）
        for (MuscleGroup g : groups.values()) {
            g.tick(packedBehavior);
        }

        // 2. 拮抗对做差（对称容错）→ float 字段
        for (AntagonisticPair pair : pairs) {
            MuscleGroupId agonistId = pair.getAgonist().getDescriptor().getId();
            float diff = pair.resolve();
            switch (agonistId) {
                case FORWARD:
                    intent.setMoveForward(diff);
                    break;
                case STRAFE_LEFT:
                    intent.setMoveStrafe(diff);
                    break;
                case LOOK_UP:
                    intent.setLookPitchDelta(diff);
                    break;
                case LOOK_LEFT:
                    intent.setLookYawDelta(diff);
                    break;
                default:
                    break;
            }
        }

        // 3. 独立肌群迟滞阈值化（判决层容错）→ boolean 字段
        intent.setJump(trigger(MuscleGroupId.JUMP));
        intent.setSneak(trigger(MuscleGroupId.SNEAK));
        intent.setAttack(trigger(MuscleGroupId.ATTACK));
        intent.setPlace(trigger(MuscleGroupId.PLACE));

        // 4. HOTBAR one-hot 解码
        intent.setHotbar(decodeHotbar(packedBehavior));

        return intent;
    }

    /**
     * 迟滞阈值化：张力超 HIGH 触发，降破 LOW 释放，中间保持上一状态。
     * 张力在 [LOW, HIGH] 迟滞带内时，误码不足以翻转——边界容错。
     */
    private boolean trigger(MuscleGroupId id) {
        float tension = groups.get(id).getTension();
        boolean prev = hysteresisState.get(id);
        if (tension > HYSTERESIS_HIGH) {
            hysteresisState.put(id, Boolean.TRUE);
            return true;
        } else if (tension < HYSTERESIS_LOW) {
            hysteresisState.put(id, Boolean.FALSE);
            return false;
        }
        return prev;
    }

    /**
     * HOTBAR one-hot 解码：前 9 bits 各代表一个 slot（1-9），
     * 取第一个 set 的 bit；全 0 表示不变。
     * <p>
     * TODO: one-hot 单 bit 容错较弱，后续可改为分组表决（每 slot 多位多数表决）
     * 或二进制+重复校验，提升 HOTBAR 可靠性。
     */
    private int decodeHotbar(int[] packed) {
        MuscleGroupDescriptor desc = layout.getGroup(MuscleGroupId.HOTBAR);
        int offset = desc.getOffset();
        for (int i = 0; i < 9; i++) {
            if (getBit(packed, offset + i)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static boolean getBit(int[] packed, int bitIndex) {
        int wordIndex = bitIndex / 32;
        int bitInWord = bitIndex % 32;
        return (packed[wordIndex] & (1 << bitInWord)) != 0;
    }

    @Override
    public void shutdown() {
        // 复位张力/迟滞状态：肌群张力积分器 + 迟滞触发状态归零，复用 intent 也 reset。
        if (groups != null) {
            for (MuscleGroup g : groups.values()) {
                g.reset();
            }
        }
        if (hysteresisState != null) {
            for (MuscleGroupId id : MuscleGroupId.values()) {
                hysteresisState.put(id, Boolean.FALSE);
            }
        }
        if (intent != null) {
            intent.reset();
        }
    }
}
