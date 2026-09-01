package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common;

/**
 * 时间方位向量 G 常量（照搬伪代码 {@code urana_constants.py}）。
 * <p>
 * 4 个 one-hot 向量，分别编码"过去第1刻 / 过去第N刻 / 未来第1刻 / 未来第N刻"4 个时间方位。
 * urana 算法用它们告诉 nn 当前推理/训练步的时间方向。
 * <p>
 * 布尔[] 而非 float[]：与伪代码一致（Python bool 列表）；由调用方（算法函数）在使用时转 CNN 的 float 载体。
 */
public final class UranaConstants {

    public static final boolean[] G_PAST_1   = {true,  false, false, false};
    public static final boolean[] G_PAST_N   = {false, true,  false, false};
    public static final boolean[] G_FUTURE_1 = {false, false, true,  false};
    public static final boolean[] G_FUTURE_N = {false, false, false, true};

    private UranaConstants() {
    }
}
