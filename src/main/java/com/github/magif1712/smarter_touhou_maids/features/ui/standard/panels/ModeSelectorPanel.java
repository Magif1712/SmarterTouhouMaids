package com.github.magif1712.smarter_touhou_maids.features.ui.standard.panels;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.core.PossessionManager;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.Registry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryEntry;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryIds;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.registry.RegistryManager;
import com.github.magif1712.smarter_touhou_maids.features.ui.standard.IConfigPanel;
import com.github.magif1712.smarter_touhou_maids.features.ui.standard.PanelContext;
import com.github.magif1712.smarter_touhou_maids.features.ui.standard.layout.ConfigRow;
import com.github.magif1712.smarter_touhou_maids.features.ui.standard.layout.VerticalStack;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.function.Function;

/**
 * Smarter 各层级模式选择面板：sensor 叶子 → agent→ai→process→nn 递归链 → effector 叶子（per-maid）。
 * <p>
 * 迁移自原 AutoTaskConfigScreen 的 buildModeSelectors/buildLeafModeSelector。
 * 递归展开逻辑不变（按选中 entry 的 subRegistryId 下钻），改用 VerticalStack 自动推进 y，
 * 且选择变化时通过 {@link PanelContext#rebuildTrigger} 触发 Screen 重建下层按钮。
 * <p>
 * sensor/effector 是与 ai 并列的叶子层（agent 下 sensor+ai+effector 三子模式），
 * subRegistryId=null 故不进递归链，独立建按钮。
 */
@OnlyIn(Dist.CLIENT)
public class ModeSelectorPanel implements IConfigPanel {
    @Override
    public Component getTitle() {
        return Component.translatable("panel.smarter_touhou_maids.mode_selector");
    }

    @Override
    public void buildWidgets(PanelContext ctx, VerticalStack stack) {
        EntityMaid maid = ctx.maid;
        if (maid == null) {
            return;
        }
        buildLeafSelector(maid, ctx, stack, RegistryIds.SENSOR);
        buildModeSelectors(maid, ctx, stack, RegistryIds.AGENT);
        buildLeafSelector(maid, ctx, stack, RegistryIds.EFFECTOR);
    }

    /**
     * 叶子层模式选择按钮（非递归，单层 CycleButton）。
     * 用于 sensor/effector 这类与 ai 并列的叶子层。subRegistryId=null，不递归。
     * <p>
     * <b>切换后触发重建</b>（真善美第2条）：叶子层虽无下层模式按钮，但切换后下游 Panel
     * （AgentDebugPanel / RuntimeParamsPanel）的调试项/参数需随新 factory 动态刷新，
     * 故同样调 {@code ctx.rebuildTrigger.run()}——与 {@link #buildModeSelectors} 一致。
     */
    private void buildLeafSelector(EntityMaid maid, PanelContext ctx, VerticalStack stack,
                                    ResourceLocation registryId) {
        Registry<?> registry = RegistryManager.INSTANCE.get(registryId);
        if (registry == null) {
            return; // 附属未注册此层，跳过
        }
        ResourceLocation currentId = PossessionManager.INSTANCE.getMode(maid, registryId);
        if (currentId == null) {
            currentId = registry.getDefaultId();
        }

        ConfigRow row = stack.addRow();
        CycleButton<ResourceLocation> btn = CycleButton.<ResourceLocation>builder(valueToText(registry))
                .withValues(registry.getAllIds())
                .withInitialValue(currentId)
                .create(row.x(), row.y(), 200, 20,
                        registryTitle(registryId),
                        (b, selectedId) -> {
                            PossessionManager.INSTANCE.setMode(maid, registryId, selectedId);
                            ctx.rebuildTrigger.run();
                        });
        row.addWidget(btn);
    }

    /**
     * 递归展开模式选择按钮（动态显隐，兼容无限层次）。
     * 选中 entry 后据其 subRegistryId 递归展开下层；subRegistryId 为 null 时停止。
     * 改选择时 callback 调 rebuildTrigger 触发 init() 重跑，下层按钮按新选择重建。
     */
    private void buildModeSelectors(EntityMaid maid, PanelContext ctx, VerticalStack stack,
                                    ResourceLocation registryId) {
        Registry<?> registry = RegistryManager.INSTANCE.get(registryId);
        if (registry == null) {
            return; // 附属未注册此层，跳过
        }
        ResourceLocation currentId = PossessionManager.INSTANCE.getMode(maid, registryId);
        if (currentId == null) {
            currentId = registry.getDefaultId();
        }

        ConfigRow row = stack.addRow();
        CycleButton<ResourceLocation> btn = CycleButton.<ResourceLocation>builder(valueToText(registry))
                .withValues(registry.getAllIds())
                .withInitialValue(currentId)
                .create(row.x(), row.y(), 200, 20,
                        registryTitle(registryId),
                        (b, selectedId) -> {
                            PossessionManager.INSTANCE.setMode(maid, registryId, selectedId);
                            ctx.rebuildTrigger.run();
                        });
        row.addWidget(btn);

        // 递归：查选中 entry 的 subRegistryId
        RegistryEntry<?> currentEntry = registry.get(currentId);
        if (currentEntry != null && currentEntry.getSubRegistryId() != null) {
            buildModeSelectors(maid, ctx, stack, currentEntry.getSubRegistryId());
        }
    }

    private static Function<ResourceLocation, Component> valueToText(Registry<?> registry) {
        return id -> {
            RegistryEntry<?> entry = registry.get(id);
            return Component.translatable(entry.getDisplayNameKey());
        };
    }

    /** registry 层标题（如 agent/ai/process/nn/sensor/effector）的 i18n key。 */
    private static Component registryTitle(ResourceLocation registryId) {
        return Component.translatable("mode.smarter_touhou_maids.registry." + registryId.getPath());
    }
}
