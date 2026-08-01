package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一层可选模式的集合（泛型，T = 该层 factory 类型）。
 * <p>
 * 一个 Registry = 一层选择（如 ai 层 / process 层 / nn 层）。持有一组 {@link RegistryEntry}，
 * 每个 entry 是该层的一个可选实现。{@link RegistryManager} 按 registryId 查找 Registry。
 * <p>
 * <b>线程安全</b>：ConcurrentHashMap。仅 mod loading 期（FMLCommonSetupEvent）可写，
 * 运行期（GUI 渲染 / ai 组装）只读。附属模组在自己的 setup event 注册。
 * <p>
 * <b>defaultId</b>：旧存档/未设置时的回退值。每个 registry 有一个默认 entry，
 * {@link #get} 查不到时调用方可 fallback 到 {@link #getDefault()}。
 *
 * @param <T> factory 类型
 */
public class Registry<T> {
    private final ResourceLocation registryId;
    private final ResourceLocation defaultId;
    private final ConcurrentHashMap<ResourceLocation, RegistryEntry<T>> entries = new ConcurrentHashMap<>();

    public Registry(ResourceLocation registryId, ResourceLocation defaultId) {
        this.registryId = registryId;
        this.defaultId = defaultId;
    }

    /**
     * 注册一个 entry（mod loading 期调用）。
     */
    public void register(RegistryEntry<T> entry) {
        entries.put(entry.getId(), entry);
    }

    /**
     * 按 id 查 entry。未注册返回 null（调用方应 fallback 到 {@link #getDefault()}）。
     */
    @Nullable
    public RegistryEntry<T> get(ResourceLocation id) {
        return entries.get(id);
    }

    /**
     * 默认 entry（旧存档/未设置时的回退）。
     */
    public RegistryEntry<T> getDefault() {
        return entries.get(defaultId);
    }

    public ResourceLocation getRegistryId() {
        return registryId;
    }

    public ResourceLocation getDefaultId() {
        return defaultId;
    }

    /**
     * 按 id 字符串解析 entry：tryParse → 查 → 查不到回退默认 entry。
     * <p>
     * 旧存档/未设置时 idStr 为空或非法，自动回退默认 entry（旧存档兼容）。
     * 各层 factory 与外周均用此方法，避免重复的 tryParse + fallback 逻辑散落各处。
     */
    public RegistryEntry<T> resolve(String idStr) {
        ResourceLocation id = ResourceLocation.tryParse(idStr);
        RegistryEntry<T> entry = (id != null) ? get(id) : null;
        if (entry == null) {
            entry = getDefault();
        }
        return entry;
    }

    /**
     * 该层所有可选 entry 的 id（GUI 据此列出选项）。
     */
    public List<ResourceLocation> getAllIds() {
        return List.copyOf(entries.keySet());
    }

    /**
     * 该层所有可选 entry（GUI 据此取 displayNameKey）。
     */
    public List<RegistryEntry<T>> getAllEntries() {
        return List.copyOf(entries.values());
    }
}
