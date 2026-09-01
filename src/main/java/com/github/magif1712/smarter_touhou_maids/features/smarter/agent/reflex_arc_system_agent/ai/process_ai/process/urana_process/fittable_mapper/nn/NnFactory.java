package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence.SaveSlot;

/**
 * NN 工厂接口：把"创建一个 nn 实例"这个不实在约束，实在化为 {@code encodingProfile()} + {@code create(...)}（真善美第4条）。
 * <p>
 * 具体工厂由外部（组合根/宿主环境）注册到 {@link NnRegistry}，UranaProcessFactory 经注册表按名解析，
 * 包内代码不 import 具体工厂（真善美第3条：加新 nn 不改 urana/process）。
 */
public interface NnFactory {

    NnEncodingProfile encodingProfile();

    INeuralNetwork create(SaveSlot slot, int inputSize, int outputSize);
}
