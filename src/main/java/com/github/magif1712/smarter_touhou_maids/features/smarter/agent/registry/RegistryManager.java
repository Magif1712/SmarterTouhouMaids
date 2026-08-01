package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 所有 Registry 的总管（单例）。
 * <p>
 * 按 registryId 查找 {@link Registry}。GUI 从顶层 registry（如 ai）开始，选中 entry 后
 * 据其 subRegistryId 递归查下层 registry，实现无限层次的动态展开。
 * <p>
 * 附属模组通过 {@link #register(Registry)} 注册自己的 registry（如新的计算层），
 * 并在自己的 entry 里声明 subRegistryId 指向它，GUI 即自动展开。
 * <p>
 * 线程安全：ConcurrentHashMap。仅 mod loading 期可写，运行期只读。
 */
public final class RegistryManager {
    public static final RegistryManager INSTANCE = new RegistryManager();

    private final ConcurrentHashMap<ResourceLocation, Registry<?>> registries = new ConcurrentHashMap<>();

    private RegistryManager() {
    }

    /**
     * 注册一个 registry（mod loading 期调用）。
     */
    public void register(Registry<?> registry) {
        registries.put(registry.getRegistryId(), registry);
    }

    /**
     * 按 registryId 查 registry。未注册返回 null。
     * <p>
     * 返回通配符类型 {@code Registry<?>}：GUI 只用 entry 的 id/displayNameKey/subRegistryId
     * （与 factory 类型无关），故通配符足够。factory 的调用发生在各层 factory 实现内部，
     * 那里持具体泛型。
     */
    @Nullable
    public Registry<?> get(ResourceLocation registryId) {
        return registries.get(registryId);
    }
}
