package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.InputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.OutputVectorDomain;

/**
 * 可拟合映射器工厂接口：把"创建一个映射器实例"这个不实在约束，实在化为 {@code create(...)}（真善美第4条）。
 * <p>
 * 具体工厂由外部（组合根/宿主环境）注册到 {@link FittableMapperRegistry}，UranaProcessFactory 经注册表按名解析，
 * 包内代码不 import 具体工厂（真善美第3条：加新映射器不改 urana/process）。
 */
public interface FittableMapperFactory {

    FittableMapper create(INeuralNetwork nn, InputVectorDomain inputDomain, OutputVectorDomain outputDomain);
}
