package com.github.magif1712.smarter_touhou_maids.features.ui.standard;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Panel 有序集合总管：所有已注册 {@link IConfigPanel} 的有序列表。
 * <p>
 * <b>独立于 agent {@code Registry}，不复用</b>（"真"）：
 * agent Registry 语义是"多选一 + resolve + default"，Panel 语义是"全部展示 + 有序遍历"，
 * 两者不同构——复用会引入 defaultId/resolve/subRegistryId 这些 Panel 用不上的字段，语义稀释。
 * 本类是轻量有序集合（register + all），直接对应 Panel 的真实语义。
 * <p>
 * <b>线程安全</b>：CopyOnWriteArrayList。仅 mod loading 期（FMLClientSetupEvent）可写，
 * 运行期（GUI 渲染）只读。附属模组在自己的 setup event 注册 Panel，主模组 Panel 先注册排在前。
 */
@OnlyIn(Dist.CLIENT)
public final class PanelRegistry {
    public static final PanelRegistry INSTANCE = new PanelRegistry();

    private final CopyOnWriteArrayList<IConfigPanel> panels = new CopyOnWriteArrayList<>();

    private PanelRegistry() {
    }

    /**
     * 注册一个 Panel（追加到末尾，mod loading 期调用）。
     * 主模组在 DefaultPanels.registerDefaults() 注册默认 4 个；附属追加自己的 Panel。
     */
    public void register(IConfigPanel panel) {
        panels.add(panel);
    }

    /** 所有已注册 Panel 的有序快照（Screen 据此遍历渲染）。 */
    public List<IConfigPanel> all() {
        return List.copyOf(panels);
    }
}
