package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Branch;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Meta;
import net.minecraft.resources.ResourceLocation;

/**
 * cnn_active_mapper 模块向 {@code MAPPER} 插槽贡献的注册入口（自包含）。
 * <p>
 * 与 {@code OriginalMapperModes}/{@code BnnMapperModes} <b>同层级</b>（定理1(4)：S/T 同层级，
 * A 在上层）；本 Branch 不占用别人的 NN 插槽，而是声明 {@code child("nn")} 指向自己的专属插槽
 * （{@link CnnActiveMapperNodeKeys#CNN_ACTIVE_MAPPER_NN}）——推论2 的结构化裁剪：
 * "选了 cnn_active_mapper 后只能选 cnn_active"。
 * <p>
 * 本模块的挂入点是既有的 {@link CnnActiveRegistration#onCollect}：由它在
 * {@code RegistryCollectEvent} 中把本 Branch 追加到已有的 {@code MAPPER} 插槽——
 * <b>不新建、不占用</b>别人的插槽。除方案 §6.3 列出的两处纯追加外，本模块唯一触碰的
 * 既有文件是 registry 层的事件定义 {@code RegistryCollectEvent}（补 {@code IModBusEvent}），
 * 那是<b>契约缺陷修复</b>而非流程改动：原 CNN 全部、urana 流程全部、effector 全部零修改。
 */
public final class CnnActiveMapperModes {
    private CnnActiveMapperModes() {
    }

    /** MAPPER 插槽中的稳定逻辑 id（lang/GUI 句柄）。 */
    public static final String MAPPER_ID = "cnn_active_mapper";

    /**
     * 构造 cnn_active_mapper 向 MAPPER 插槽贡献的 Branch（含 nn child → 其专属 NN 插槽）。
     *
     * @param modId 模组 id（用于构造 ResourceLocation 与显示名 key）。
     */
    public static Branch<FittableMapper> mapperBranch(String modId) {
        Branch<FittableMapper> branch = new Branch<>(
                new ResourceLocation(modId, MAPPER_ID),
                new CnnActiveMapperFactory(),
                new Meta("mode." + modId + ".mapper.cnn_active_mapper", 0, modId));
        branch.addChild("nn", CnnActiveMapperNodeKeys.CNN_ACTIVE_MAPPER_NN);
        return branch;
    }
}
