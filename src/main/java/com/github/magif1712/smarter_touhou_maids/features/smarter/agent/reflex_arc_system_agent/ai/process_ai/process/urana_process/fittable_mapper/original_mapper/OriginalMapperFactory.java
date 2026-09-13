package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.original_mapper;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapperFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.InputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.OutputVectorDomain;

/**
 * Urana 映射器工厂（叶子）：{@code createMapped(...)} 直接新建 {@link OriginalMapper}。
 * nn 的组装（provider→profile→domain→实例）由 {@link FittableMapperFactory} 的 default 方法承载。
 */
public class OriginalMapperFactory implements FittableMapperFactory {

    @Override
    public FittableMapper createMapped(INeuralNetwork nn, InputVectorDomain inputDomain, OutputVectorDomain outputDomain) {
        return new OriginalMapper(nn, inputDomain, outputDomain);
    }
}
