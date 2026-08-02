package com.github.magif1712.smarter_touhou_maids.features.smarter.agent;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * maid 来源抽象（客户端）。
 * <p>
 * smarter 通用层（{@link SmarterClientService}）需要"当前操作的 maid"来组装 config、判断
 * smarterReady、sync 激活态，但 maid 来源是 agent 特有的模式：ReflexArcSystemAgent 的 maid 来自
 * 附身（{@code PossessionManager.getPossessedMaid}），未来不依赖附身的 agent 可能有别的来源。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第3条</b>：X（SmarterClientService，上层）依赖 Y（maid 来源，下层模式）的抽象而非具体。
 *       Y 可切换为附身(Y1) 或其他来源(Y2)，X 不改代码即可正确运行。</li>
 *   <li><b>第4条</b>：把"maid 来源"这个不实在的概念，实在化为一个接口。</li>
 * </ul>
 * <p>
 * <b>注入方向</b>：具体实现由 agent 的 sensor 子系统在 client setup 阶段反向注册给
 * {@link SmarterClientService#setMaidSource}（依赖方向：reflex_arc → 通用层，而非通用层 → reflex_arc）。
 */
@FunctionalInterface
@OnlyIn(Dist.CLIENT)
public interface MaidSource {
    /**
     * 获取当前 maid 上下文。
     *
     * @return 当前 maid；无 maid 时返回 null（如未附身）
     */
    @Nullable
    EntityMaid get();
}
