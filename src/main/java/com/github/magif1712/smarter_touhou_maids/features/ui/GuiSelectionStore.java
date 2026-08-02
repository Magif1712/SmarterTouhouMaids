package com.github.magif1712.smarter_touhou_maids.features.ui;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.Registry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryManager;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.ConfigGuiIds;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.UUID;

/**
 * per-maid 配置 GUI 选择的 session 级存储（类比 PossessionManager 的 pending 缓存模式）。
 * <p>
 * 纯内存 {@code HashMap<UUID, ResourceLocation>}，session 级——退出游戏即丢。
 * 符合需求"关闭界面也可以保存，不需要退出游戏还保存"。
 * <p>
 * <b>不放进 PossessionManager</b>（"真"）：
 * PossessionManager 语义是"附身状态 + per-maid 模式同步"，GUI 选择是另一域（UI 导航），
 * 混入会稀释 PossessionManager 语义。独立 store 更"真"。
 * <p>
 * <b>不需网络同步</b>：GUI 选择纯客户端（Screen 是客户端对象），服务端不关心客户端用什么 GUI 渲染。
 */
@OnlyIn(Dist.CLIENT)
public final class GuiSelectionStore {
    public static final GuiSelectionStore INSTANCE = new GuiSelectionStore();

    private final HashMap<UUID, ResourceLocation> selection = new HashMap<>();

    private GuiSelectionStore() {
    }

    /**
     * 读 per-maid 选中 GUI id；未设置回退 registry 默认。
     *
     * @param maidUUID 女仆 UUID
     * @return 选中 GUI id，registry 未注册时返回 null
     */
    @Nullable
    public ResourceLocation get(UUID maidUUID) {
        ResourceLocation id = selection.get(maidUUID);
        if (id != null) {
            return id;
        }
        Registry<?> registry = RegistryManager.INSTANCE.get(ConfigGuiIds.CONFIG_GUI);
        return registry != null ? registry.getDefaultId() : null;
    }

    /**
     * 读 per-maid 选中 GUI id；maid 为 null 时回退默认。
     */
    @Nullable
    public ResourceLocation get(@Nullable EntityMaid maid) {
        return maid != null ? get(maid.getUUID()) : null;
    }

    /**
     * 写 per-maid 选中 GUI id（session 级保存）。
     */
    public void set(UUID maidUUID, ResourceLocation guiId) {
        selection.put(maidUUID, guiId);
    }
}
