package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 契约机制（推论2）单测：requires 声明、可满足性求解、GUI 可选集过滤、freeze 契约校验。
 * <p>
 * 测试图镜像目标结构：agent ─┬─ sensor（push[旧] / pull[新]）
 *                           └─ ai → process（legacy 需 push / modern 需 pull）
 */
class ConstraintSolverTest {

    // 契约接口（模拟 IPushEncodedSensor / IPullEncodedSensor）
    private interface SensorBase {
    }

    private interface PushSensor extends SensorBase {
    }

    private interface PullSensor extends SensorBase {
    }

    private static final ResourceLocation AGENT = new ResourceLocation("stm", "agent");
    private static final ResourceLocation SENSOR = new ResourceLocation("stm", "sensor");
    private static final ResourceLocation AI = new ResourceLocation("stm", "ai");
    private static final ResourceLocation PROCESS = new ResourceLocation("stm", "process");

    private static <T> Factory<T> factory(Class<T> type) {
        return new Factory<>() {
            @Override
            public Class<T> producedType() {
                return type;
            }

            @Override
            public void create(AssemblyContext ctx, /*->*/ OutSlot<T> out) {
            }
        };
    }

    private static Meta meta() {
        return new Meta("t", 0, "stm");
    }

    private static final NodeKey<Object> AGENT_KEY = new NodeKey<>(AGENT, Object.class);
    private static final NodeKey<Object> SENSOR_KEY = new NodeKey<>(SENSOR, Object.class);
    private static final NodeKey<Object> PROCESS_KEY = new NodeKey<>(PROCESS, Object.class);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RegistrySnapshot buildTree() {
        TreeRegistry reg = new TreeRegistry();
        Node agent = reg.node(AGENT_KEY);
        Branch<Object> reflex = new Branch<>(new ResourceLocation("stm", "reflex"), factory(Object.class), meta());
        reflex.addChild("sensor", SENSOR_KEY);
        reflex.addChild("ai", new NodeKey<>(AI, Object.class));
        agent.addBranch(reflex);
        agent.defaultBranch(new ResourceLocation("stm", "reflex"));

        Node sensor = reg.node(SENSOR_KEY);
        sensor.addBranch(new Branch(new ResourceLocation("stm", "push_sensor"), factory(PushSensor.class), meta()));
        sensor.addBranch(new Branch(new ResourceLocation("stm", "pull_sensor"), factory(PullSensor.class), meta()));
        sensor.defaultBranch(new ResourceLocation("stm", "pull_sensor")); // 新版为默认

        Node ai = reg.node(new NodeKey<>(AI, Object.class));
        Branch<Object> processAi = new Branch<>(new ResourceLocation("stm", "process_ai"), factory(Object.class), meta());
        processAi.addChild("process", PROCESS_KEY);
        ai.addBranch(processAi);
        ai.defaultBranch(new ResourceLocation("stm", "process_ai"));

        Node process = reg.node(PROCESS_KEY);
        Branch<Object> legacy = new Branch<>(new ResourceLocation("stm", "legacy_process"), factory(Object.class), meta());
        legacy.addRequirement(SENSOR, PushSensor.class);
        process.addBranch(legacy);
        Branch<Object> modern = new Branch<>(new ResourceLocation("stm", "modern_process"), factory(Object.class), meta());
        modern.addRequirement(SENSOR, PullSensor.class);
        process.addBranch(modern);
        process.defaultBranch(new ResourceLocation("stm", "modern_process"));

        return reg.freeze(java.util.Set.of(AGENT) /*->*/);
    }

    @Test
    void 全默认可满足() {
        RegistrySnapshot snap = buildTree();
        assertNull(ConstraintSolver.checkDefaultsSatisfiable(snap.nodes()::get, AGENT_KEY));
        assertTrue(ConstraintSolver.isSatisfiable(snap.nodes()::get, AGENT_KEY, Selection.empty()));
    }

    @Test
    void 单固定legacy_可满足_sensor自由取push() {
        RegistrySnapshot snap = buildTree();
        Selection sel = Selection.empty().with(PROCESS_KEY, new ResourceLocation("stm", "legacy_process"));
        assertTrue(ConstraintSolver.isSatisfiable(snap.nodes()::get, AGENT_KEY, sel));
    }

    @Test
    void 双固定不兼容_不可满足() {
        RegistrySnapshot snap = buildTree();
        Selection sel = Selection.empty()
                .with(PROCESS_KEY, new ResourceLocation("stm", "legacy_process"))
                .with(SENSOR_KEY, new ResourceLocation("stm", "pull_sensor"));
        assertFalse(ConstraintSolver.isSatisfiable(snap.nodes()::get, AGENT_KEY, sel));
    }

    @Test
    void 双固定兼容_可满足() {
        RegistrySnapshot snap = buildTree();
        Selection sel = Selection.empty()
                .with(PROCESS_KEY, new ResourceLocation("stm", "legacy_process"))
                .with(SENSOR_KEY, new ResourceLocation("stm", "push_sensor"));
        assertTrue(ConstraintSolver.isSatisfiable(snap.nodes()::get, AGENT_KEY, sel));
    }

    @Test
    void GUI过滤_固定legacy后sensor两方向都可选_级联兜底() {
        // 新语义（死锁修复）：process 是 sensor 的兄弟而非祖先——sensor 过滤只看祖先链，
        // 两个感受器都可选；选了不兼容的那个时由级联修正把 process 重置为兼容分支。
        RegistrySnapshot snap = buildTree();
        Selection sel = Selection.empty().with(PROCESS_KEY, new ResourceLocation("stm", "legacy_process"));
        Set<ResourceLocation> selectable = new LinkedHashSet<>();
        ConstraintSolver.selectableBranches(snap.nodes()::get, AGENT_KEY, SENSOR_KEY, sel, /*->*/ selectable);
        assertEquals(Set.of(new ResourceLocation("stm", "push_sensor"), new ResourceLocation("stm", "pull_sensor")),
                selectable);
    }

    @Test
    void GUI过滤_固定pull后process两方向都可选_级联兜底() {
        RegistrySnapshot snap = buildTree();
        Selection sel = Selection.empty().with(SENSOR_KEY, new ResourceLocation("stm", "pull_sensor"));
        Set<ResourceLocation> selectable = new LinkedHashSet<>();
        ConstraintSolver.selectableBranches(snap.nodes()::get, AGENT_KEY, PROCESS_KEY, sel, /*->*/ selectable);
        assertEquals(Set.of(new ResourceLocation("stm", "legacy_process"), new ResourceLocation("stm", "modern_process")),
                selectable);
    }

    @Test
    void fixed指向不存在分支_视为未设置() {
        RegistrySnapshot snap = buildTree();
        Selection sel = Selection.empty().with(PROCESS_KEY, new ResourceLocation("stm", "ghost"));
        assertTrue(ConstraintSolver.isSatisfiable(snap.nodes()::get, AGENT_KEY, sel));
    }

    /**
     * 回归（兄弟固化死锁 bug）：sensor 已固化 push 时，PROCESS 插槽的过滤不得锁死——
     * 两个 process 分支都应可选（兄弟条目由级联修复，不参与过滤判定）。切到旧流程后
     * 必须能切回新流程。
     */
    @Test
    void 兄弟固化条目不锁死目标插槽选项() {
        RegistrySnapshot snap = buildTree();
        Selection sel = Selection.empty().with(SENSOR_KEY, new ResourceLocation("stm", "push_sensor"));
        Set<ResourceLocation> selectable = new LinkedHashSet<>();
        ConstraintSolver.selectableBranches(snap.nodes()::get, AGENT_KEY, PROCESS_KEY, sel, /*->*/ selectable);
        assertEquals(Set.of(new ResourceLocation("stm", "legacy_process"), new ResourceLocation("stm", "modern_process")),
                selectable);
    }

    /**
     * 回归：全固定不兼容时贪心最大保留——以 process=modern 为锚，push 被丢弃，
     * 见证 sensor=pull（级联修复）。
     */
    @Test
    void 见证赋值_不兼容组合经自由变量修复() {
        RegistrySnapshot snap = buildTree();
        // 只固定 process=modern（sensor 自由）→ 见证应补全 sensor=pull
        Selection sel = Selection.empty().with(PROCESS_KEY, new ResourceLocation("stm", "modern_process"));
        Map<String, ResourceLocation> witness = new java.util.HashMap<>();
        Map<ResourceLocation, ResourceLocation> raw = new java.util.LinkedHashMap<>();
        assertTrue(ConstraintSolver.solveAssignment(snap.nodes()::get, AGENT_KEY, sel, /*->*/ raw));
        raw.forEach((k, v) -> witness.put(k.toString(), v));
        assertEquals(new ResourceLocation("stm", "pull_sensor"), witness.get("stm:sensor"));
        assertEquals(new ResourceLocation("stm", "modern_process"), witness.get("stm:process"));
    }

    @Test
    void freeze_死需求报错() {
        TreeRegistry reg = new TreeRegistry();
        Node<Object> agent = reg.node(AGENT_KEY);
        Branch<Object> reflex = new Branch<>(new ResourceLocation("stm", "reflex"), factory(Object.class), meta());
        agent.addBranch(reflex);
        agent.defaultBranch(new ResourceLocation("stm", "reflex"));
        reflex.addRequirement(SENSOR, PushSensor.class); // SENSOR 未注册 → 目标不存在
        List<String> errors = new ArrayList<>();
        GraphValidator.validate(reg.nodes(), java.util.Set.of(AGENT), /*->*/ errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("目标插槽不存在")));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void freeze_全默认不满足契约_报错() {
        TreeRegistry reg = new TreeRegistry();
        Node<Object> agent = reg.node(AGENT_KEY);
        Branch<Object> reflex = new Branch<>(new ResourceLocation("stm", "reflex"), factory(Object.class), meta());
        reflex.addChild("sensor", SENSOR_KEY);
        reflex.addChild("ai", new NodeKey<>(AI, Object.class));
        agent.addBranch(reflex);
        agent.defaultBranch(new ResourceLocation("stm", "reflex"));

        Node sensor = reg.node(SENSOR_KEY);
        sensor.addBranch(new Branch(new ResourceLocation("stm", "pull_sensor"), factory(PullSensor.class), meta()));
        sensor.defaultBranch(new ResourceLocation("stm", "pull_sensor"));

        Node<Object> ai = reg.node(new NodeKey<>(AI, Object.class));
        Branch<Object> processAi = new Branch<>(new ResourceLocation("stm", "process_ai"), factory(Object.class), meta());
        processAi.addChild("process", PROCESS_KEY);
        ai.addBranch(processAi);
        ai.defaultBranch(new ResourceLocation("stm", "process_ai"));

        Node<Object> process = reg.node(PROCESS_KEY);
        Branch<Object> legacy = new Branch<>(new ResourceLocation("stm", "legacy_process"), factory(Object.class), meta());
        legacy.addRequirement(SENSOR, PushSensor.class); // 默认 sensor 是 pull → 全默认不可满足
        process.addBranch(legacy);
        process.defaultBranch(new ResourceLocation("stm", "legacy_process"));

        List<String> errors = new ArrayList<>();
        GraphValidator.validate(reg.nodes(), java.util.Set.of(AGENT), /*->*/ errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("全默认路径不满足契约")), errors.toString());
    }
}
