package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn;

import java.util.HashMap;
import java.util.Map;

/**
 * NN 工厂注册表：按名字解析 {@link NnFactory}，包内代码不 import 具体工厂。
 * <p>
 * 具体工厂由外部（组合根/宿主环境）注册，UranaProcessFactory 经本表按名解析（真善美第3条：加新 nn 不改 urana/process）。
 */
public final class NnRegistry {
    private static final Map<String, NnFactory> factories = new HashMap<>();

    private NnRegistry() {
    }

    public static void register(String name, NnFactory nnFactory) {
        factories.put(name, nnFactory);
    }

    public static NnFactory resolve(String name) {
        return factories.get(name);
    }
}
