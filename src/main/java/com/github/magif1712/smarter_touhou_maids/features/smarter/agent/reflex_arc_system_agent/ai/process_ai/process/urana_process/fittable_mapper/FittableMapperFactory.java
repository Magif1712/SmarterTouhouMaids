package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnEncodingProfile;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.IODomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.InputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.OutputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.AssemblyContext;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Factory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.OutSlot;

/**
 * 可拟合映射器工厂契约：树原生 {@link Factory}（概念树重构后）。
 * <p>
 * <b>提供者模式解 nn 尺寸环</b>：mapper 的 child 是 NN 插槽，其产物是 {@link NnFactory}
 * <b>提供者</b>（非实例）——nn 实例化需要尺寸，而尺寸 = nn 编码剖面 × urana domain 倍数
 * （鸡生蛋问题：domain 要 profile，profile 来自 nn 类，nn 实例要尺寸）。工厂经
 * {@code nnProvider.encodingProfile()} 无实例查询剖面，建 domain 算总长，再带参实例化 nn。
 * urana 的布局知识（{@link IODomain}）由本接口的 default 组装统一承载，具体 mapper 工厂
 * 只剩 {@link #createMapped}（nn + domain → mapper 实例）一行差异。
 * <p>
 * 设计原则（真善美第3条）：加新映射器不改 urana/process——实现本接口即可。
 */
public interface FittableMapperFactory extends Factory<FittableMapper> {

    /**
     * 本 mapper 家族的唯一差异点：用装好的 nn 与 domain 构造 mapper 实例。
     */
    FittableMapper createMapped(INeuralNetwork nn, InputVectorDomain inputDomain, OutputVectorDomain outputDomain);

    /**
     * 树原生组装：nn 提供者 child → profile → IODomain → nn 实例 → mapper 实例。
     * 子插槽名固定为 {@code "nn"}（mapper 分支的 NN child 声明名）。
     */
    @Override
    default void create(AssemblyContext ctx, /*->*/ OutSlot<FittableMapper> out) {
        NnFactory nnProvider = ctx.child("nn", NnFactory.class);
        NnEncodingProfile profile = nnProvider.encodingProfile();
        IODomain ioDomain = new IODomain(profile);
        INeuralNetwork nn = nnProvider.create(ctx.saveSlot(),
                ioDomain.getInputDomain().totalLength(), ioDomain.getOutputDomain().totalLength());
        out.set(createMapped(nn, ioDomain.getInputDomain(), ioDomain.getOutputDomain()));
    }

    @Override
    default Class<FittableMapper> producedType() {
        return FittableMapper.class;
    }
}
