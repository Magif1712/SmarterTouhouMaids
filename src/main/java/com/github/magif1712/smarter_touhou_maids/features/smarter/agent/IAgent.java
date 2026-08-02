package com.github.magif1712.smarter_touhou_maids.features.smarter.agent;

/**
 * smarter 模式的顶层契约：感知→思考→行动的整个外周过程。
 * <p>
 * agent 是"smarter 接管女仆后整个外周机制"的抽象。它承载视觉采集、AI 运行、
 * 效应器解码、发包的完整链路。不同的 agent 实现代表不同的"感知-思考-行动"组合方式
 * （如 ReflexArcSystemAgent 用 PossessionSensor 感受 + urana 思考 + BionicMuscleEffector 效应；附属可实现其他组合）。
 * <p>
 * <b>与 IAiSystem 的层次关系</b>（真善美第2条：模式1在上层）：
 * agent 是 ai 的上层——ai 是 agent 内部的"思考"机制，agent 还包含"感知"和"行动"。
 * 故 AgentRegistry 是顶层 registry，subRegistryId 指向 AiRegistry。
 * <p>
 * <b>注入对称</b>（镜像 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.ProcessAiSystem}）：
 * ProcessAiSystem 只注入 IProcessSystem（换 process 零改动）；ReflexArcSystemAgent 注入
 * {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.IAiSystem}
 * + {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.ISensor}
 * + {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.IEffector}
 * （换 ai/sensor/effector 任一零改动）。每个类只注入自己直接使用的那个抽象。
 * <p>
 * <b>生命周期由 {@link SmarterClientService} 管理</b>：SmarterClientService 是事件总线入口
 * 与生命周期容器，持 IAgent 引用，委托执行。IAgent 不感知 Forge 事件订阅，只关心
 * "被唤醒后如何运转、被关闭时如何释放"。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：C 中"agent = 感知→思考→行动的外周过程"是 agent 的模式。
 *       感受细节（vision/glBlitFramebuffer）、思考细节（process/nn）、行动细节（张力积分/迟滞）
 *       分别藏在 sensor/ai/effector 实现里，不进本接口，也不进 ReflexArcSystemAgent。</li>
 *   <li><b>第3条</b>：把"agent 可选"这个不实在的约束，实在化为 IAgent 接口 +
 *       AgentRegistry + ReflexArcSystemAgentFactory。</li>
 * </ul>
 * <p>
 * <b>激活自决</b>（{@link #isActive}）：smarter 的"启用"是自动任务模式的固有行为（通用），
 * 但 agent 是否"激活"（真正接管、tick、抑制原版 AI）由 agent 自决。ReflexArcSystemAgent 因依赖
 * 附身前置而 isActive=附身；其他 agent 无前置时默认 isActive=true。SmarterClientService 据此决定
 * 是否 tick + 是否 sync 激活状态到服务端（服务端 mixin 只读标量，不依赖下层激活条件——真善美第3条）。
 */
public interface IAgent {
    /**
     * 唤醒 agent：创建共享资源（感觉缓冲区、CUDA 流、完成事件、行为通道等），
     * 唤醒感受器/效应器/AI（启动 AI 工作线程）。
     * <p>
     * 调用方（SmarterClientService.init）已在 create(config) 时注入下层 ai+sensor+effector，
     * 本方法只负责创建 agent 自有共享资源并唤醒三者。
     */
    void awaken();

    /**
     * 每 client tick 调用：消费 AI 产出的行为输出，解码为操作要求，发包到服务端。
     * <p>
     * SmarterClientService 已用 {@link #isActive()} 守卫，本方法仅在 agent 激活上下文中调用。
     * 故本方法内部不再重复检测激活条件（如附身状态）。
     */
    void onClientTick();

    /**
     * 渲染后调用：视觉采集（屏幕快照→感觉缓冲区），在 agent 的 CUDA 流上异步执行。
     *
     * @param textureId 渲染结果的 OpenGL 纹理 id
     * @param texWidth  纹理宽度
     * @param texHeight 纹理高度
     */
    void onPostRender(int textureId, int texWidth, int texHeight);

    /**
     * 关闭：停止 AI 工作线程，释放共享资源（感觉缓冲区、CUDA 流、完成事件、行为通道）
     * + sensor/effector/ai 各自 shutdown，并发一帧零操作要求让 maid 停止。
     */
    void shutdown();

    /**
     * agent 是否激活（应真正接管、tick、抑制原版 AI）。
     * <p>
     * 由 agent 自决激活条件：ReflexArcSystemAgent 因依赖附身前置而 isActive=附身；
     * 无前置条件的 agent 默认 true（自动任务模式开启即激活）。
     * <p>
     * SmarterClientService 据此决定是否 tick + 是否 sync 激活状态到服务端
     * （服务端 {@code MobServerAiStepSuppressMixin} 只读 sync 后的标量，不依赖下层激活条件——
     * 真善美第3条：换 agent 激活条件时服务端 mixin 零改动）。
     * <p>
     * 默认 true：意识域 C 中"无前置 agent 直接激活"是一个模式，D 中 default true 实在化它。
     * 依赖前置（如附身）的 agent 覆盖本方法。
     *
     * @return true=激活（接管中）；false=未激活（原版 AI 正常，smarter 不发包）
     */
    default boolean isActive() {
        return true;
    }
}
