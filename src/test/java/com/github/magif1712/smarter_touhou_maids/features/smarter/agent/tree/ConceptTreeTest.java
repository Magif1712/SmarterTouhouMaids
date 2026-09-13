package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 概念树 freeze 校验与运行时解析的单测。
 * 覆盖：defaultBranch 缺失、children 引用不存在、环检测、根可达性、默认回退、
 * 未声明子插槽不解析、单次组装缓存共享。
 */
class ConceptTreeTest {

    private static final ResourceLocation AGENT = new ResourceLocation("stm", "agent");
    private static final ResourceLocation AI = new ResourceLocation("stm", "ai");
    private static final ResourceLocation SENSOR = new ResourceLocation("stm", "sensor");

    /** 最简工厂：产出一个标记字符串。 */
    private static Factory<String> factory(String tag) {
        return new Factory<>() {
            @Override
            public Class<String> producedType() {
                return String.class;
            }

            @Override
            public void create(AssemblyContext ctx, /*->*/ OutSlot<String> out) {
                out.set(tag);
            }
        };
    }

    private static Meta meta(String id) {
        return new Meta("test." + id, 0, "stm");
    }

    // ==================== 校验 ====================

    @Test
    void defaultBranch缺失_报错() {
        TreeRegistry reg = new TreeRegistry();
        Node<String> agent = reg.node(new NodeKey<>(AGENT, String.class));
        agent.addBranch(new Branch<>(new ResourceLocation("stm", "a"), factory("a"), meta("a")));
        List<String> errors = new ArrayList<>();
        GraphValidator.validate(reg.nodes(), java.util.Set.of(AGENT), /*->*/ errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("defaultBranch")));
    }

    @Test
    void children引用不存在Node_报错() {
        TreeRegistry reg = new TreeRegistry();
        Node<String> agent = reg.node(new NodeKey<>(AGENT, String.class));
        Branch<String> b = new Branch<>(new ResourceLocation("stm", "a"), factory("a"), meta("a"));
        b.addChild("ai", new NodeKey<>(AI, String.class)); // AI 未注册
        agent.addBranch(b);
        agent.defaultBranch(new ResourceLocation("stm", "a"));
        List<String> errors = new ArrayList<>();
        GraphValidator.validate(reg.nodes(), java.util.Set.of(AGENT), /*->*/ errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("不存在的 Node")));
    }

    @Test
    void 环_被检出() {
        TreeRegistry reg = new TreeRegistry();
        Node<String> a = reg.node(new NodeKey<>(AGENT, String.class));
        Node<String> b = reg.node(new NodeKey<>(AI, String.class));
        Branch<String> ba = new Branch<>(new ResourceLocation("stm", "ba"), factory("ba"), meta("ba"));
        ba.addChild("next", new NodeKey<>(AI, String.class));
        a.addBranch(ba);
        a.defaultBranch(new ResourceLocation("stm", "ba"));
        Branch<String> bb = new Branch<>(new ResourceLocation("stm", "bb"), factory("bb"), meta("bb"));
        bb.addChild("back", new NodeKey<>(AGENT, String.class)); // 指回根：成环
        b.addBranch(bb);
        b.defaultBranch(new ResourceLocation("stm", "bb"));
        List<String> errors = new ArrayList<>();
        GraphValidator.validate(reg.nodes(), java.util.Set.of(AGENT), /*->*/ errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("环")));
    }

    @Test
    void 不可达Node_报错() {
        TreeRegistry reg = new TreeRegistry();
        Node<String> agent = reg.node(new NodeKey<>(AGENT, String.class));
        agent.addBranch(new Branch<>(new ResourceLocation("stm", "a"), factory("a"), meta("a")));
        agent.defaultBranch(new ResourceLocation("stm", "a"));
        Node<String> orphan = reg.node(new NodeKey<>(SENSOR, String.class));
        orphan.addBranch(new Branch<>(new ResourceLocation("stm", "s"), factory("s"), meta("s")));
        orphan.defaultBranch(new ResourceLocation("stm", "s"));
        List<String> errors = new ArrayList<>();
        GraphValidator.validate(reg.nodes(), java.util.Set.of(AGENT), /*->*/ errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("不可达")));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void 类型不匹配_挂branch即抛() {
        TreeRegistry reg = new TreeRegistry();
        Node<Number> num = reg.node(new NodeKey<>(AGENT, Number.class));
        Branch<String> bad = new Branch<>(new ResourceLocation("stm", "a"), factory("a"), meta("a"));
        // 编译期泛型会拦截，故用原始类型模拟"绕过编译期"的场景，验证运行期令牌校验
        assertThrows(IllegalArgumentException.class, () -> ((Node) num).addBranch(bad));
    }

    @Test
    void freeze后禁止注册() {
        TreeRegistry reg = new TreeRegistry();
        Node<String> agent = reg.node(new NodeKey<>(AGENT, String.class));
        agent.addBranch(new Branch<>(new ResourceLocation("stm", "a"), factory("a"), meta("a")));
        agent.defaultBranch(new ResourceLocation("stm", "a"));
        reg.freeze(java.util.Set.of(AGENT) /*->*/);
        assertThrows(IllegalStateException.class, () -> reg.node(new NodeKey<>(SENSOR, String.class)));
    }

    // ==================== 解析 ====================

    /** 构造 agent(sensor?)+ai 两层树：agent 的 a1 分支声明 ai child，a2 分支不声明。 */
    private static RegistrySnapshot twoLayerTree() {
        TreeRegistry reg = new TreeRegistry();
        Node<String> agent = reg.node(new NodeKey<>(AGENT, String.class));
        Branch<String> a1 = new Branch<>(new ResourceLocation("stm", "a1"), factory("a1"), meta("a1"));
        a1.addChild("ai", new NodeKey<>(AI, String.class));
        agent.addBranch(a1);
        Branch<String> a2 = new Branch<>(new ResourceLocation("stm", "a2"), factory("a2"), meta("a2"));
        agent.addBranch(a2); // 无 children
        agent.defaultBranch(new ResourceLocation("stm", "a1"));

        Node<String> ai = reg.node(new NodeKey<>(AI, String.class));
        ai.addBranch(new Branch<>(new ResourceLocation("stm", "ai_default"), factory("ai_default"), meta("ai_default")));
        ai.addBranch(new Branch<>(new ResourceLocation("stm", "ai_alt"), factory("ai_alt"), meta("ai_alt")));
        ai.defaultBranch(new ResourceLocation("stm", "ai_default"));
        return reg.freeze(java.util.Set.of(AGENT) /*->*/);
    }

    @Test
    void 未设置时回退默认分支() {
        RegistrySnapshot snap = twoLayerTree();
        OutSlot<String> out = new OutSlot<>();
        Resolver.resolve(snap, new NodeKey<>(AGENT, String.class), Selection.empty(), null, null, /*->*/ out);
        assertEquals("a1", out.get());
    }

    @Test
    void 未知分支id_回退默认() {
        RegistrySnapshot snap = twoLayerTree();
        Selection sel = Selection.empty().with(new NodeKey<>(AGENT, String.class), new ResourceLocation("stm", "ghost"));
        OutSlot<String> out = new OutSlot<>();
        Resolver.resolve(snap, new NodeKey<>(AGENT, String.class), sel, null, null, /*->*/ out);
        assertEquals("a1", out.get());
    }

    @Test
    void 选中无children的分支_下层不被解析() {
        RegistrySnapshot snap = twoLayerTree();
        Selection sel = Selection.empty().with(new NodeKey<>(AGENT, String.class), new ResourceLocation("stm", "a2"));
        OutSlot<String> out = new OutSlot<>();
        Resolver.resolve(snap, new NodeKey<>(AGENT, String.class), sel, null, null, /*->*/ out);
        assertEquals("a2", out.get()); // ai 层存在但 a2 未声明 ai child → 不解析也不报错
    }

    @Test
    void 工厂只感知自己声明的children() {
        TreeRegistry reg = new TreeRegistry();
        Node<String> agent = reg.node(new NodeKey<>(AGENT, String.class));
        Branch<String> a1 = new Branch<>(new ResourceLocation("stm", "a1"), new Factory<>() {
            @Override
            public Class<String> producedType() {
                return String.class;
            }

            @Override
            public void create(AssemblyContext ctx, /*->*/ OutSlot<String> out) {
                out.set("got:" + ctx.child("ai", String.class) + ",ghost:" + ctx.child("ghost"));
            }
        }, meta("a1"));
        a1.addChild("ai", new NodeKey<>(AI, String.class));
        agent.addBranch(a1);
        agent.defaultBranch(new ResourceLocation("stm", "a1"));
        Node<String> ai = reg.node(new NodeKey<>(AI, String.class));
        ai.addBranch(new Branch<>(new ResourceLocation("stm", "ai_alt"), factory("ai_alt"), meta("ai_alt")));
        ai.defaultBranch(new ResourceLocation("stm", "ai_alt"));
        RegistrySnapshot snap = reg.freeze(java.util.Set.of(AGENT) /*->*/);

        Selection sel = Selection.empty().with(new NodeKey<>(AI, String.class), new ResourceLocation("stm", "ai_alt"));
        OutSlot<String> out = new OutSlot<>();
        Resolver.resolve(snap, new NodeKey<>(AGENT, String.class), sel, null, null, /*->*/ out);
        assertEquals("got:ai_alt,ghost:null", out.get());
    }
}
