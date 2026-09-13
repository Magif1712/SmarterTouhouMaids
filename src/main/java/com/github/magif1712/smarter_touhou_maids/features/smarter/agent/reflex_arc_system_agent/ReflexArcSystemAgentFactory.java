package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.maid.compat.task.AutoTask;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.AgentFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.IAgent;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamPanelProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.IAiSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.EffectorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.IEffector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.ISensor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.SensorFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.AssemblyContext;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.OutSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Smarter agent 的 {@link AgentFactory} 实现。
 * <p>
 * <b>工厂自驱组装三件</b>（真善美第1条"真"）：ReflexArcSystemAgent 构造注入 ai+sensor+effector 三者，
 * 故本工厂直接使用这三个抽象，自行查三个 registry 取下层 factory 创建它们，再 new ReflexArcSystemAgent(ai, sensor, effector)。
 * 外周（SmarterClientService）不感知任何 registry 的存在——它只调本工厂。
 * <p>
 * sensor/effector 是与 ai 并列的叶子层（agent 下 sensor+ai+effector 三个子模式），各自独立查询、无递归
 * （它们 subRegistryId=null）。ai 仍按原 subRegistryId 链自驱组装 process/nn。
 * <p>
 * config 里各 id 缺失/非法时回退对应 registry 默认 entry（{@link Registry#resolve} 内置 fallback）。
 * <p>
 * 附属 agent 的工厂实现可不查这些 registry（如纯规则 agent 不需要 ai/sensor/effector），直接造自己的 agent。
 * <p>
 * <b>参数项</b>（实现 {@link ParamPanelProvider}）：暴露"允许附身"开关（per-maid 布尔）。
 * 附身是 ReflexArcSystemAgent 的 PossessionSensor 前置——本 agent 依赖附身锁定 maid 并采集其视角，
 * 故"允许附身"是本 agent 特有配置。换 agent 不实现 ParamPanelProvider 时自动消失（GUI 零改动），
 * 与 minDt 经 UranaProcessFactory 暴露同机制（真善美第2条：数据驱动、跟随 agent）。
 */
public class ReflexArcSystemAgentFactory implements AgentFactory, ParamPanelProvider {

    /**
     * 本分支在 AGENT registry 的 entry id（{@code AiModeDefaults} 注册 smarter 时引用）。
     * possession 等分支私有模式的守卫以此比对 maid 选中的 agent（多代理共存，D4 形态修正）。
     * dist 中立（客户端输入守卫 / 服务端附身请求守卫共用）。
     */
    public static final ResourceLocation AGENT_ID =
            new ResourceLocation(SmarterTouhouMaids.MOD_ID, "smarter");

    @Override
    public void create(AssemblyContext ctx, /*->*/ OutSlot<IAgent> out) {
        // === 子实例来自 Resolver（本分支声明的 children：ai/sensor/effector）===
        // ai 由 AI 链自下而上装好（process/mapper/nn 各层自驱，本工厂不感知）。
        IAiSystem ai = ctx.child("ai", IAiSystem.class);

        // === provider 模式：sensor/effector 插槽产物是工厂提供者，实例化需要跨兄弟参数
        //     （feelingSize/behaviorSize 是 ai 层 Domain 知识）——由本工厂带参实例化 ===
        SensorFactory sensorFactory = ctx.child("sensor", SensorFactory.class);
        ISensor sensor = sensorFactory.create(ai.feelingSize());
        EffectorFactory effectorFactory = ctx.child("effector", EffectorFactory.class);
        IEffector effector = effectorFactory.create(ai.behaviorSize());

        // 三注入构造（ReflexArcSystemAgent 只直接用 ai/sensor/effector，不感知 process/nn/vision/muscle）
        out.set(new ReflexArcSystemAgent(ai, sensor, effector));
    }

    @Override
    public List<ParamOption> getParamOptions() {
        // "允许附身"开关（per-maid 布尔）。getter/setter 复用 AutoTask 的 per-maid 持久化。
        // boolean parse 直接在 lambda 里——太简单，不需要工具方法。
        // tooltip 用 Component.keybind 显示玩家实际绑定的附身键（改键后自动更新），
        // 并说明"附身后才激活 smarter AI"——避免玩家找不到附身方式或误以为开关即激活。
        return List.of(
                ParamOption.of(
                        Component.translatable("option.smarter_touhou_maids.allow_possession"),
                        Component.translatable(
                                "option.smarter_touhou_maids.allow_possession.tooltip",
                                Component.keybind("key.smarter_touhou_maids.possession")),
                        maid -> String.valueOf(AutoTask.isPossessionEnabled(maid)),
                        (maid, text) -> AutoTask.setPossessionEnabled(maid, Boolean.parseBoolean(text.trim())))
                .withControlHint("toggle"));
    }
}