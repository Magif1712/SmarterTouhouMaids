package com.github.magif1712.smarter_touhou_maids.features.smarter.agent;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.PersistableProvider;
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
 * <b>随各层模式动态切换</b>：每次调用都实时读 {@link SmarterClientState#getMode} 获取当前选中 entry，
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
        ResourceLocation currentId = SmarterClientState.INSTANCE.getMode(maid, registryId);
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
        ResourceLocation currentId = SmarterClientState.INSTANCE.getMode(maid, registryId);
        if (currentId == null) {
            currentId = registry.getDefaultId();
        }
        RegistryEntry<?> entry = registry.get(currentId);
        if (entry != null) {
            onFactory.accept(registryId, entry.getFactory());
        }
    }

    // ==================== 持久化支持 ====================

    /**
     * 推导 maid 当前完整根叶路径 token（如 "smarter__process_ai__urana__standard_bnn"）。
     * <p>
     * 沿递归链（agent → ai → process → nn）逐层下钻，收集每层选中 entry 的 id path 分量，
     * 用 {@code __} 拼接。sensor/effector 叶子层不纳入（持久化数据按 AI 实现路径键控，与感受器/效应器无关）。
     * <p>
     * 用 entry id 的 path 分量（如 {@code smarter_touhou_maids:smarter} → {@code smarter}），
     * 保持目录名简洁可读。跨 mod 同 path 分量碰撞风险可接受（本项目各层 entry 同 mod）。
     * <p>
     * <b>用实在的东西转化不实在的概念</b>（真善美第4条）：把"根叶路径"这个不实在的概念，
     * 实在化为一个可做目录名/配置 key 的字符串 token。
     *
     * @param maid 女仆实体（per-maid 模式选择）
     * @return 路径 token（无选中时返回空串）
     */
    public static String pathToken(EntityMaid maid) {
        StringBuilder sb = new StringBuilder();
        pathTokenChain(maid, RegistryIds.AGENT, sb);
        return sb.toString();
    }

    /**
     * 递归链 token 拼接：从当前层选中 entry 取 id path 分量拼接，再据 subRegistryId 下钻。
     */
    private static void pathTokenChain(EntityMaid maid, ResourceLocation registryId, StringBuilder sb) {
        Registry<?> registry = RegistryManager.INSTANCE.get(registryId);
        if (registry == null) {
            return;
        }
        ResourceLocation currentId = SmarterClientState.INSTANCE.getMode(maid, registryId);
        if (currentId == null) {
            currentId = registry.getDefaultId();
        }
        RegistryEntry<?> entry = registry.get(currentId);
        if (entry == null) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("__");
        }
        sb.append(entry.getId().getPath());
        if (entry.getSubRegistryId() != null) {
            pathTokenChain(maid, entry.getSubRegistryId(), sb);
        }
    }

    /**
     * 路径默认持久化开关 = 路径上任一 factory 实现 {@link PersistableProvider} 且 {@code hasPersistableData()=true}。
     * <p>
     * 与 ParamPanelProvider/DebugPanelProvider 的遍历检查同构（第三 Provider 管道）。
     * 遍历递归链 + 叶子层所有 factory，任一声明 true 即路径默认开。
     * <p>
     * <b>声明轨</b>（真善美第2条）：本方法返回路径的默认持久化开关，供 GUI 初值与 shutdown 判断。
     * 用户可在 GUI 覆盖（存 ParamStore，per-maid）。
     *
     * @param maid 女仆实体
     * @return true = 路径上任一 factory 声明有可持久化数据
     */
    public static boolean anyPersistable(EntityMaid maid) {
        boolean[] result = {false};
        walk(maid, (registryId, factory) -> {
            if (factory instanceof PersistableProvider p && p.hasPersistableData()) {
                result[0] = true;
            }
        });
        return result[0];
    }
}
