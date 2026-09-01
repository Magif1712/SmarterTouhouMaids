package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper;

import java.util.HashMap;
import java.util.Map;

/**
 * 可拟合映射器工厂注册表：按名字解析 {@link FittableMapperFactory}，包内代码不 import 具体工厂。
 * <p>
 * 具体工厂由外部（组合根/宿主环境）注册，UranaProcessFactory 经本表按名解析
 * （真善美第3条：加新映射器不改 urana/process）。
 */
public final class FittableMapperRegistry {
    private static final Map<String, FittableMapperFactory> factories = new HashMap<>();

    private FittableMapperRegistry() {
    }

    public static void register(String name, FittableMapperFactory mapperFactory) {
        factories.put(name, mapperFactory);
    }

    public static FittableMapperFactory resolve(String name) {
        return factories.get(name);
    }
}
