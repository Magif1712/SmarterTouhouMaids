package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.bionic_muscle_effector.muscle;

/**
 * 肌张力积分器：低通滤波，对应肌肉的收缩时间常数。
 * <p>
 * 生物对应：肌肉是物理对象，不能瞬间全力收缩/舒张。运动神经元放电频率 ~20-50Hz，
 * 但肌肉张力是放电的时间积分（肌纤维低通滤波，时间常数 τ_muscle ≈ 30-100ms）。
 * 单次神经冲动不会让肌肉瞬间响应，单帧噪声被时间平滑——这是时间容错。
 * <p>
 * 递推：{@code t[n] = (1-α)·t[n-1] + α·input[n]}，
 * 对应连续 τ = Δt·(1-α)/α。tick=50ms，α=0.5 → τ≈50ms（人肌肉时间常数），
 * 效应器自身延迟约 50ms，加上 Urana 200ms，总反应 ≈ 250ms ≈ 人反应速度。
 * <p>
 * 时间容错的主体是“Urana 5Hz 决策 vs 效应器 20Hz 执行”的频率差——
 * 两次 Urana 输出之间 4 个 tick 用同一份激活强度，效应器在 200ms 内持续执行，
 * 单 tick 抖动不改变 200ms 周期内的整体行为。本积分器只负责切换处的平滑过渡。
 */
public class TensionIntegrator {

    private float current = 0f;
    private final float alpha;

    /**
     * @param alpha 平滑系数，取值 (0,1]。α=1 表示无滤波（瞬时响应），越小越平滑但响应越慢。
     */
    public TensionIntegrator(float alpha) {
        if (alpha <= 0f || alpha > 1f) {
            throw new IllegalArgumentException("alpha must be in (0,1], got " + alpha);
        }
        this.alpha = alpha;
    }

    /**
     * 用瞬时激活强度更新张力。
     *
     * @param activation 瞬时激活强度 [0,1]（来自运动单元池加权求和）。
     * @return 平滑后的张力 [0,1]。
     */
    public float update(float activation) {
        current = (1f - alpha) * current + alpha * activation;
        return current;
    }

    public float getCurrent() {
        return current;
    }

    public void reset() {
        current = 0f;
    }
}
