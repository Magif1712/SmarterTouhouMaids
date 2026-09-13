package com.github.magif1712.smarter_touhou_maids.features.ui;

import com.github.magif1712.smarter_touhou_maids.features.maid.menu.AutoTaskConfigMenu;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Branch;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.ConceptTree;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Node;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.RegistrySnapshot;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.ConfigGuiFactory;
import com.github.magif1712.smarter_touhou_maids.features.ui.config_gui.ConfigGuiIds;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 配置 GUI 选择层入口 Screen（替代 AutoTaskConfigScreen 作为 {@code MenuScreens.register} 的绑定）。
 * <p>
 * <b>模式层级</b>（真善美第2条）：选择器（本类）是上层，配置 GUI 是下层。选择器换一种配置 GUI
 * （附属增减 entry）时，选择器代码零改动即正确运行。
 * <p>
 * <b>两步打开流程</b>：用户先选 GUI 类型（CycleButton），再点"打开"按钮进入具体配置 GUI。
 * 不直接打开具体 GUI——符合需求"选好了之后点击按钮就可以打开具体操作的gui"。
 * <p>
 * <b>共享 menu</b>：{@code openSelected()} 调 {@code setScreen(factory.create(menu, inventory, title))}
 * 创建新 Screen，共享同一个 {@link AutoTaskConfigMenu}（Minecraft 持有的容器）。
 * 同一容器的不同 Screen 视图切换，不产生孤儿 menu。
 * <p>
 * <b>per-maid session 保存</b>：选择存入 {@link GuiSelectionStore}（内存 HashMap），
 * 关闭界面不丢失，退出游戏重置为默认。
 *
 * @see GuiSelectionStore
 * @see ConfigGuiFactory
 */
@OnlyIn(Dist.CLIENT)
public class GuiSelectorScreen extends AbstractContainerScreen<AutoTaskConfigMenu> {
    private static final int BG_COLOR = 0xCC000000;
    private static final int GUI_WIDTH = 280;
    private static final int GUI_HEIGHT = 160;
    private static final int TITLE_COLOR = 0x39c5bb;

    @Nullable
    private ResourceLocation selectedId;
    /** 玩家背包：AbstractContainerScreen 在 1.20.1 不存 inventory 字段（只存 playerInventoryTitle），
     *  本类需在打开下层 GUI 时把它传给 ConfigGuiFactory，故自行保存。 */
    private final Inventory inventory;

    public GuiSelectorScreen(AutoTaskConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.inventory = inventory;
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        EntityMaid maid = this.menu.getMaid();
        RegistrySnapshot snapshot = ConceptTree.snapshot();
        if (snapshot == null) {
            return;
        }
        Node<ConfigGuiFactory> node = snapshot.node(ConfigGuiIds.CONFIG_GUI);
        if (node == null) {
            return;
        }

        // 当前选择（含默认回退）
        this.selectedId = GuiSelectionStore.INSTANCE.get(maid);
        if (this.selectedId == null || node.branch(this.selectedId) == null) {
            this.selectedId = node.defaultBranch();
        }
        if (this.selectedId == null) {
            return;
        }

        int cx = this.leftPos + this.imageWidth / 2;
        int selectorY = this.topPos + 50;

        // GUI 类型选择按钮（与 ModeSelectorPanel 的 CycleButton 风格一致）
        CycleButton<ResourceLocation> selectorButton = CycleButton.<ResourceLocation>builder(id -> {
            Branch<?> branch = node.branch(id);
            return branch != null
                    ? Component.translatable(branch.meta().displayNameKey())
                    : Component.literal(id.toString());
        })
                .withValues(List.copyOf(node.branches().keySet()))
                .withInitialValue(this.selectedId)
                .create(cx - 100, selectorY, 200, 20,
                        Component.translatable("gui.smarter_touhou_maids.selector.choose"),
                        (b, id) -> this.selectedId = id);
        this.addRenderableWidget(selectorButton);

        // 打开按钮
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.smarter_touhou_maids.selector.open"),
                        b -> openSelected())
                .bounds(cx - 100, selectorY + 30, 95, 20).build());

        // 取消按钮
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.smarter_touhou_maids.selector.cancel"),
                        b -> Minecraft.getInstance().setScreen(null))
                .bounds(cx + 5, selectorY + 30, 95, 20).build());
    }

    /**
     * 打开选中的配置 GUI：保存选择 → 创建 Screen → setScreen 切换。
     * <p>
     * factory 即分支产物本身（provider 自产：CONFIG_GUI 插槽的分支产物是 ConfigGuiFactory）。
     */
    private void openSelected() {
        if (this.selectedId == null) {
            return;
        }
        RegistrySnapshot snapshot = ConceptTree.snapshot();
        if (snapshot == null) {
            return;
        }
        Node<ConfigGuiFactory> node = snapshot.node(ConfigGuiIds.CONFIG_GUI);
        if (node == null) {
            return;
        }
        Branch<ConfigGuiFactory> branch = node.branch(this.selectedId);
        if (branch == null) {
            ResourceLocation def = node.defaultBranch();
            branch = def != null ? node.branch(def) : null;
        }
        if (branch == null) {
            return;
        }

        // 保存 per-maid 选择（session 级）
        EntityMaid maid = this.menu.getMaid();
        if (maid != null) {
            GuiSelectionStore.INSTANCE.set(maid.getUUID(), this.selectedId);
        }

        // 创建并切换到选中的 Screen（共享 menu）
        // provider 自产：分支的 factory 对象即 ConfigGuiFactory 自身（freeze 期类型校验已保证），受控强转
        ConfigGuiFactory factory = (ConfigGuiFactory) branch.factory();
        Minecraft.getInstance().setScreen(factory.create(this.menu, this.inventory, this.title));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int startX = (this.width - this.imageWidth) / 2;
        int startY = (this.height - this.imageHeight) / 2;
        guiGraphics.fill(startX, startY, startX + this.imageWidth, startY + this.imageHeight, BG_COLOR);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font,
                Component.translatable("gui.smarter_touhou_maids.selector.title"),
                this.titleLabelX, this.titleLabelY, TITLE_COLOR, true);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.smarter_touhou_maids.selector.hint"),
                this.titleLabelX, this.titleLabelY + 15, 0xAAAAAA, false);
    }
}
