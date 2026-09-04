package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.debug;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamStore;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.ActionIntent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 效应器调试钩子：效仿 {@link VisionDebugHook}（感受器调试）的“开关 + 观察”上层模式，
 * 把效应器每 tick 产出的 {@link ActionIntent} 转成人话输出到终端。
 * <p>
 * 模式分层（真善美第2条：上层模式相同，下层实现因领域不同而不同）：
 * <ul>
 *   <li>上层（与感受器调试同构）：开关（per-maid 存 ParamStore）+ 读开关 + 入口短路。</li>
 *   <li>下层（与感受器调试不同构，这是真善美而非强行同构）：
 *       感受器数据在 GPU，需自驱 {@code @SubscribeEvent} + copyToHost + 重建图像 + 写盘；
 *       效应器输出（ActionIntent）已在 CPU，正常路径已产出，故本钩子<b>被动</b>由
 *       {@code ReflexArcSystemAgent.onClientTick} 在效应器 {@code tick} 产出 intent 后
 *       调用 {@link #log}——无需订阅 tick 事件，无需重复解码。</li>
 * </ul>
 * <p>
 * <b>per-maid 开关</b>（真善美第1条“善”：消除 global state）：开关状态不再以单例 volatile 字段留存，
 * 改存 {@link ParamStore}（maid NBT，随存档走，网络同步）。{@link #log} 每次调用读 ParamStore(maid) 判断。
 * 主线程 onClientTick 20Hz 调用，读 ParamStore（HashMap 查询）开销微，无需缓存。
 * 与 dtDebug 的“缓存到字段”模式不同——dtDebug 在 GPU 高频线程，需 setDtDebugEnabled 缓存；
 * 本钩子在低频主线程，直读即可（消费频率差异决定模式差异，真善美而非强行同构）。
 * <p>
 * <b>零性能损失核心</b>（真善美第3条：把“是否在调试中”这个不实在概念用实在的 ParamStore 读取固化）：
 * 关闭时 {@link #log} 第一行即 return，后续格式化全不执行。
 */
@OnlyIn(Dist.CLIENT)
public enum EffectorDebugHook {
    INSTANCE;

    private static final Logger LOGGER = LoggerFactory.getLogger("EffectorDebug");

    /** 连续值视为零的阈值：|v| ≤ 此值显示为“—”，避免低通滤波残余刷屏。 */
    private static final float DEAD_ZONE = 0.005f;

    /** per-maid NBT key（存 ParamStore，随 maid 存档走）。public 供 factory 声明 ParamOption 引用。 */
    public static final String KEY_EFFECTOR_DEBUG_ENABLED = "effectorDebugEnabled";

    /**
     * 读 per-maid 开关（供 factory 声明 ParamOption 回显 + log 消费点判断）。
     * maid 为 null 时返回默认 false。
     */
    public static boolean isEffectorDebugEnabled(EntityMaid maid) {
        return Boolean.parseBoolean(
                ParamStore.INSTANCE.getString(maid, KEY_EFFECTOR_DEBUG_ENABLED, "false"));
    }

    /**
     * 观察并输出一帧 {@link ActionIntent} 到终端。
     * <p>
     * 零性能损失核心：关闭时第一行即返回，后续格式化全不执行。
     * 由 {@code ReflexArcSystemAgent.onClientTick} 在效应器 {@code tick} 产出 intent 后调用。
     *
     * @param intent 本 tick 解码出的操作要求（复用实例，调用方立即消费）。
     * @param maid   当前女仆（per-maid 读 ParamStore 判断开关）。
     */
    public void log(ActionIntent intent, EntityMaid maid) {
        // 零性能损失核心：关闭时第一行即返回。
        if (!isEffectorDebugEnabled(maid)) return;
        LOGGER.info("[EffectorDebug] {}", formatIntent(intent));
    }

    /**
     * 把 {@link ActionIntent} 转成人话。连续值带方向标签（正负语义见 ActionIntent 字段注释），
     * |v| ≤ DEAD_ZONE 显示“—”；布尔用 是/否；hotbar=0 显示“不变”。
     */
    private static String formatIntent(ActionIntent a) {
        StringBuilder sb = new StringBuilder(80);
        sb.append(fmt(a.getMoveForward(), "前进", "后退")).append(' ');
        // moveStrafe：正=右移（对齐 Minecraft strafe 约定），故正→右移、负→左移。
        sb.append(fmt(a.getMoveStrafe(), "右移", "左移")).append(' ');
        sb.append(fmt(a.getLookPitchDelta(), "抬头", "低头")).append(' ');
        sb.append(fmt(a.getLookYawDelta(), "左转", "右转")).append(' ');
        sb.append("跳=").append(a.isJump() ? "是" : "否").append(' ');
        sb.append("蹲=").append(a.isSneak() ? "是" : "否").append(' ');
        sb.append("攻=").append(a.isAttack() ? "是" : "否").append(' ');
        sb.append("放=").append(a.isPlace() ? "是" : "否").append(' ');
        sb.append("栏=").append(a.getHotbar() == 0 ? "不变" : a.getHotbar());
        return sb.toString();
    }

    /**
     * 格式化连续值：v &gt; DEAD_ZONE → posLabel+v；v &lt; -DEAD_ZONE → negLabel+|v|；否则“—”。
     */
    private static String fmt(float v, String posLabel, String negLabel) {
        if (v > DEAD_ZONE) return posLabel + String.format("%.2f", v);
        if (v < -DEAD_ZONE) return negLabel + String.format("%.2f", -v);
        return "—";
    }
}
