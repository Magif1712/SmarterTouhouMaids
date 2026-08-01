package com.github.magif1712.smarter_touhou_maids.features.smarter.agent;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.core.PossessionManager;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.Registry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiConsumer;

/**
 * 遍历 smarter 模式各层选中 entry 的 factory（真善美第2条：意识域 C 中"遍历各层选中模式 factory"
 * 是<b>一个</b>模式，代码域 D 中也只应有<b>一个</b>实现，故提取为共享遍历器）。
 * <p>
 * <b>遍历顺序</b>：
 * <ol>
 *   <li>递归链：agent → ai → process → nn（按选中 entry 的 subRegistryId 下钻）</li>
 *   <li>叶子层：sensor、effector（与 ai 并列，不递归）</li>
 * </ol>
 * <p>
 * <b>用实在的东西转化不实在的概念</b>（真善美第4条）：把"遍历各层 factory"这个不实在的算法流程，
 * 实在化为一个类 + 一个回调契约。调用方只需关心"拿到 factory 后做什么"（渲染 EditBox / CycleButton），
 * 不关心遍历细节。两个 Panel（RuntimeParamsPanel / AgentDebugPanel）共用同一遍历器，零重复。
 * <p>
 * <b>Factory 级别</b>：遍历的是 factory（注册时就存在，不依赖 agent 实例），故附身前即可调用。
 * <p>
 * <b>随各层模式动态切换</b>：每次调用都实时读 {@link PossessionManager#getMode} 获取当前选中 entry，
 * ModeSelectorPanel 切换后 rebuildWidgets → 重新 walk → 各 Panel 拿到新 factory 列表 → 动态刷新。
 */
public final class SmarterLayerWalker {
    private SmarterLayerWalker() {
    }

    /**
     * 遍历 maid 当前选中的所有层 factory，对每个 factory 调 onFactory 回调。
     * <p>
     * 调用方典型用法：
     * <pre>{@code
     * SmarterLayerWalker.walk(maid, (registryId, factory) -> {
     *     if (factory instanceof ParamPanelProvider) {
     *         // 渲染参数项
     *     }
     * });
     * }</pre>
     *
     * @param maid      女仆实体（per-maid 模式选择）
     * @param onFactory 回调：(registryId, factory) — registryId 标识当前层，factory 是该层选中 entry 的工厂
     */
    public static void walk(EntityMaid maid, BiConsumer<ResourceLocation, Object> onFactory) {
        // 递归链: agent → ai → process → nn
        walkChain(maid, RegistryIds.AGENT, onFactory);
        // 叶子层（与 ai 并列）
        walkLeaf(maid, RegistryIds.SENSOR, onFactory);
        walkLeaf(maid, RegistryIds.EFFECTOR, onFactory);
    }

    /**
     * 递归链遍历：从当前层选中 entry 取 factory 调回调，再据 subRegistryId 下钻。
     */
    private static void walkChain(EntityMaid maid, ResourceLocation registryId,
                                   BiConsumer<ResourceLocation, Object> onFactory) {
        Registry<?> registry = RegistryManager.INSTANCE.get(registryId);
        if (registry == null) {
            return; // 附属未注册此层，跳过
        }
        ResourceLocation currentId = PossessionManager.INSTANCE.getMode(maid, registryId);
        if (currentId == null) {
            currentId = registry.getDefaultId();
        }
        RegistryEntry<?> entry = registry.get(currentId);
        if (entry == null) {
            return;
        }
        onFactory.accept(registryId, entry.getFactory());
        if (entry.getSubRegistryId() != null) {
            walkChain(maid, entry.getSubRegistryId(), onFactory);
        }
    }

    /**
     * 叶子层遍历：从当前层选中 entry 取 factory 调回调（不递归）。
     * 用于 sensor/effector 这类与 ai 并列的叶子层，subRegistryId=null。
     */
    private static void walkLeaf(EntityMaid maid, ResourceLocation registryId,
                                  BiConsumer<ResourceLocation, Object> onFactory) {
        Registry<?> registry = RegistryManager.INSTANCE.get(registryId);
        if (registry == null) {
            return; // 附属未注册此层，跳过
        }
        ResourceLocation currentId = PossessionManager.INSTANCE.getMode(maid, registryId);
        if (currentId == null) {
            currentId = registry.getDefaultId();
        }
        RegistryEntry<?> entry = registry.get(currentId);
        if (entry != null) {
            onFactory.accept(registryId, entry.getFactory());
        }
    }
}
