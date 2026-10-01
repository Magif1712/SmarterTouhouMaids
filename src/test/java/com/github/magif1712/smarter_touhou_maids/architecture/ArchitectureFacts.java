package com.github.magif1712.smarter_touhou_maids.architecture;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 架构事实清单（冻结清单）——W0 安全网的<b>唯一数据源</b>。
 * <p>
 * <b>为什么需要本类</b>（真善美第4条"用实在的东西把不实在的东西转化为实在的东西"）：
 * "不得破坏旧存档 / 不得破坏 native 绑定"这条约束若只写在文档里就是不实在的；
 * 把它实在化为<b>可被 JUnit 断言的常量清单</b>，改写就会让测试变红。
 * <p>
 * <b>校验分工</b>：
 * <ul>
 *   <li>{@link FrozenIdsTest}：清单 ↔ 生产源码（id 字面量 / lang key / 协议标识）双向比对。</li>
 *   <li>{@link ThreeWayMirrorTest}：清单 ↔ 目录实况（空目录基线 / 待整改结构）。</li>
 * </ul>
 * <p>
 * <b>冻结的语义</b>：{@link #DEFAULT_PATH_TOKEN} 是持久化目录 key（{@code SaveSlot.rootPath()} 的
 * {@code <path-token>} 段，见 {@code SmarterLayerWalker.pathToken}）——改一个 id 就让旧存档的权重目录失配，
 * 故 id 与 token 一经登记不得变更；<b>包名/目录名不受此约束</b>（token 取自 {@code branch.id().getPath()}）。
 * <p>
 * <b>字形</b>：本类是纯数据清单（值语义读取），故按设计原则5 的 G7 例外<b>保留 public static final 字段</b>，
 * 不引入 getter 方法。
 */
public final class ArchitectureFacts {

    private ArchitectureFacts() {
    }

    /** 模组 id（所有 ResourceLocation 的 namespace 来源）。 */
    public static final String MOD_ID = "smarter_touhou_maids";

    /** 11 个插槽 id 的 path 部分（namespace 恒为 {@link #MOD_ID}）。 */
    public static final List<String> NODE_PATHS = List.of(
            "agent", "ai", "sensor", "effector",
            "process", "mapper",
            "original_mapper_nn", "bnn_mapper_nn", "cnn_active_mapper_nn",
            "config_gui");

    /** 每个插槽下注册的全部分支 id 的 path 部分。 */
    public static final Map<String, List<String>> BRANCHES_BY_NODE = Map.ofEntries(
            Map.entry("agent", List.of("smarter")),
            Map.entry("ai", List.of("process_ai")),
            Map.entry("sensor", List.of("possession_sensor", "on_demand_possession_sensor")),
            Map.entry("effector", List.of("bionic_muscle_effector")),
            Map.entry("process", List.of("urana")),
            Map.entry("mapper", List.of("original_mapper", "bnn_mapper", "cnn_active_mapper")),
            Map.entry("original_mapper_nn", List.of("cnn")),
            Map.entry("bnn_mapper_nn", List.of("bnn", "standard_bnn")),
            Map.entry("cnn_active_mapper_nn", List.of("cnn_active")),
            Map.entry("config_gui", List.of("default")));

    /** 每个插槽的默认分支（path 部分）。 */
    public static final Map<String, String> DEFAULT_BRANCH_BY_NODE = Map.ofEntries(
            Map.entry("agent", "smarter"),
            Map.entry("ai", "process_ai"),
            Map.entry("sensor", "on_demand_possession_sensor"),
            Map.entry("effector", "bionic_muscle_effector"),
            Map.entry("process", "urana"),
            Map.entry("mapper", "original_mapper"),
            Map.entry("original_mapper_nn", "cnn"),
            Map.entry("bnn_mapper_nn", "bnn"),
            Map.entry("cnn_active_mapper_nn", "cnn_active"),
            Map.entry("config_gui", "default"));

    /** {@code Branch.children} 的具名集合（{@code SmarterLayerWalker} 的 {@code subLayerId} 依赖其名字）。 */
    public static final List<String> CHILD_NAMES = List.of(
            "ai", "sensor", "effector", "process", "mapper", "nn");

    /**
     * 新版主轴默认链的持久化 token（即 {@code SaveSlot.rootPath()} 的 path-token 段）。
     * <p>
     * 复现 {@code SmarterLayerWalker.layerViewOf} 的三条语义：默认走 {@code defaultBranch}、
     * 分量取 {@code branch.id().getPath()}、下层取第一个非 {@code sensor}/{@code effector} 的 child。
     */
    public static final String DEFAULT_PATH_TOKEN =
            "smarter__process_ai__urana__original_mapper__cnn";

    // 旧版链 token（smarter__process_ai__urana_original__standard_bnn）随旧版包于 W3b 下线而取消：
    // 旧存档里选中 urana_original 的记录，加载时按 Resolver 规则回退到默认分支（urana）；权重文件仍在磁盘。

    /** mapper 切到 bnn_mapper 时的 token（其专属 NN 插槽默认 bnn）。 */
    public static final String BNN_MAPPER_PATH_TOKEN =
            "smarter__process_ai__urana__bnn_mapper__bnn";

    /** mapper 切到 cnn_active_mapper 时的 token。 */
    public static final String CNN_ACTIVE_PATH_TOKEN =
            "smarter__process_ai__urana__cnn_active_mapper__cnn_active";

    /** lang 文件里全部 {@code mode.*} key（含 {@link #ORPHAN_LANG_KEYS}）。 */
    public static final List<String> LANG_MODE_KEYS = List.of(
            "mode.smarter_touhou_maids.agent.smarter",
            "mode.smarter_touhou_maids.agent.smarter_original",
            "mode.smarter_touhou_maids.ai.process_ai",
            "mode.smarter_touhou_maids.effector.bionic_muscle_effector",
            "mode.smarter_touhou_maids.effector.bionic_muscle_effector_original",
            "mode.smarter_touhou_maids.mapper.bnn_mapper",
            "mode.smarter_touhou_maids.mapper.cnn_active_mapper",
            "mode.smarter_touhou_maids.mapper.original_mapper",
            "mode.smarter_touhou_maids.nn.bnn",
            "mode.smarter_touhou_maids.nn.cnn",
            "mode.smarter_touhou_maids.nn.cnn_active",
            "mode.smarter_touhou_maids.nn.standard_bnn",
            "mode.smarter_touhou_maids.process.urana",
            "mode.smarter_touhou_maids.registry.agent",
            "mode.smarter_touhou_maids.registry.ai",
            "mode.smarter_touhou_maids.registry.bnn_mapper_nn",
            "mode.smarter_touhou_maids.registry.cnn_active_mapper_nn",
            "mode.smarter_touhou_maids.registry.effector",
            "mode.smarter_touhou_maids.registry.mapper",
            "mode.smarter_touhou_maids.registry.original_mapper_nn",
            "mode.smarter_touhou_maids.registry.process",
            "mode.smarter_touhou_maids.registry.sensor",
            "mode.smarter_touhou_maids.sensor.on_demand_possession_sensor",
            "mode.smarter_touhou_maids.sensor.possession_sensor");

    /**
     * 已登记的<b>孤儿 lang key</b>：符号系统里有、概念树里无对应分支。
     * <p>
     * 真善美第2条（A′ 中没有的模式 A 中也不要有痕迹）：这两个 key 应在后续工作包中删除；
     * 删除后本清单必须同步缩小（{@link FrozenIdsTest} 会强制要求）。
     */
    public static final List<String> ORPHAN_LANG_KEYS = List.of(
            "mode.smarter_touhou_maids.agent.smarter_original",
            "mode.smarter_touhou_maids.effector.bionic_muscle_effector_original");

    /** 网络通道 / 哨兵标识（协议级，改名即失配）。 */
    public static final List<String> PROTOCOL_IDS = List.of("main", "none");

    /** ParamStore 的 per-maid 键（命名空间形如 {@code smarter_touhou_maids._<key>}）。 */
    public static final List<String> PARAM_STORE_KEYS = List.of("CnnActiveActivationId");

    /** 权重文件名（{@code SaveSlot.layerPath(layerId)} 之下）。 */
    public static final List<String> WEIGHT_FILE_NAMES = List.of(
            "b_original.bin", "p.bin", "q.bin", "l.bin", "r.bin", "b.bin");

    /**
     * {@code ResourceLocation} 字面量的完整冻结集合（path 部分）。
     * <p>
     * {@link FrozenIdsTest} 扫描生产源码里所有 {@code new ResourceLocation(MOD_ID|modId, "...")}
     * 与 {@code static final String *_ID = "..."}，断言其结果<b>恰好</b>等于本集合（多一个少一个都红）。
     */
    public static final Set<String> FROZEN_ID_LITERALS = Set.of(
            // 插槽
            "agent", "ai", "sensor", "effector", "process", "mapper",
            "original_mapper_nn", "bnn_mapper_nn", "cnn_active_mapper_nn", "config_gui",
            // 分支
            "smarter", "process_ai", "possession_sensor", "on_demand_possession_sensor",
            "bionic_muscle_effector", "urana",
            "original_mapper", "bnn_mapper", "cnn_active_mapper",
            "cnn", "bnn", "standard_bnn", "cnn_active", "default",
            // 协议 / 哨兵
            "main", "none");

    /** native 侧 JNI 实现规模的冻结基线（含 native 方法的类数）。 */
    public static final int NATIVE_CLASS_BASELINE = 18;
    /** native 侧 JNI 实现规模的冻结基线（native 方法总数，与 18 个 *_jni.cpp 的 JNIEXPORT 数一致）。 */
    public static final int NATIVE_METHOD_BASELINE = 83;

    /**
     * 生产代码里 DPS 方向标记的出现次数基线（{@code ->} 218 + {@code <-} 278 = 496）。
     * <p>
     * {@link DpsShapeTest} 断言实际次数 {@code >=} 本基线：重构只应<b>增加</b>标记（把违规方法改成 DPS）。
     * <p>
     * <b>W3b 记录</b>：旧版包（{@code urana_original}）整代下线使标记由 176 降到 174——
     * 这是"删掉一整代实现"的合法减少，基线随之下调（清单必须与实物同步）。
     * <p>
     * <b>W4 记录</b>：core 层 33 处"写自身"方法在<b>定义点</b>补上 {@code <-}（调用点早已带该标记，
     * 只是定义点漏写）→ 169。基线随之上调，锁住这批已达标的契约。
     * <p>
     * <b>W5 记录</b>：core 层出参右置（{@code VectorMappings} 三个静态方法、
     * {@code readMappedToJava}/{@code copyToHost} 的重载、{@code readTo}）→ 218；
     * 参数顺序变化同步改了 11 处调用点（含 1 处测试辅助方法）。
     * <p>
     * <b>W6 记录</b>：{@link DpsShapeTest} 修掉一个漏检（javadoc 之后的方法签名会被注释行污染而跳过），
     * 立刻暴露 2 个"有返回值却带方向标记"的方法（{@code TreeRegistry.freeze}、
     * {@code ConstraintSolver.ancestorFixedSelection}），已改成 void + {@code OutSlot}；
     * 同时移除 2 处"无出参却标出参分界"的误标（{@code ConceptTree.freeze} 及其调用点）→ 40 + 176 = 216。
     * <p>
     * <b>W10 记录</b>：tree 层 5 处"写自身"方法（{@code Branch.meta/addChild/addRequirement}、
     * {@code Node.defaultBranch/addBranch}）在定义点补 {@code <-}，并按 G3（调用点同形）
     * 给 48 处调用点补标记 → 224。
     * <p>
     * <b>W12 记录</b>：<b>修正统计口径</b>——项目里同时使用 {@code /*->*&#47;} 与 {@code /* -> *&#47;}
     * 两种写法，此前只统计前者，使覆盖率被低估近一半（真值 438）。同批补齐 persistence/effector 的
     * 4 处（{@code PathTokenLogic.appendChain}、{@code ActionIntent.writeTo}、
     * {@code MuscleGroup.reset}、{@code TensionIntegrator.reset}）及其调用点。
     * <p>
     * <b>W14 记录</b>：统一"生命周期 / 装配注入"方法族的标记——{@code ISensor}/{@code IEffector}/
     * {@code IAgent} 的 {@code awaken/capture/shutdown/save} 与 {@code setRefreshRequest}/
     * {@code setVisionEncoder} 此前<b>全缺</b>（而同族的 {@code IAiSystem}/{@code IProcessSystem}
     * 早已带 {@code <-}），补齐接口、实现与调用点共 43 处 → 273。
     * <p>
     * <b>W15 记录</b>：BNN 机制层 3 处"出参未右置"完成右置（{@code BnnOps.negateAndBinarize}/
     * {@code negateAndBinarizeRegion}、{@code BnnGradientProcessor.calculateOutputLayerGradient}）
     * + 4 处调用点同步 → 215。
     * <p>
     * <b>W17 记录</b>：补上 ② 的"<b>无参写自身</b>"覆盖边界——{@code VectorBase.releaseResource}
     * （abstract + 3 个子类实现 + 1 处调用点）；并完成 {@code BnnNetworkProcessor.backward} /
     * {@code backwardWithGradientDescent} 的出参右置（1 处调用点同步）→ 218 + 278 = 496。
     * <p>
     * <b>至此两轮启发式体检均清零</b>：①"参数含缓冲区类型且无标记"、②"无参 void 且无标记"
     * 都只剩**静态注册方法**（{@code registerDefaults}/{@code init}，无接收者故无须标记）与
     * 框架强制签名（{@code close}）。注意：这是"启发式覆盖",不是形式化证明——
     * 仍可能存在"出参是自定义非缓冲区类型"的漏网情形。
     */
    public static final int DIRECTION_MARK_BASELINE = 496;

    // ==================== 结构层事实（W2 起：Java↔native 对应关系） ====================
    //
    // 依据（定理1(4) + 审计结论）：
    //   · 装配图 = ConceptTree（代码即装配图：S/T 同层、A 在上层包含 S 与 T）
    //   · JNI 符号串必须与 Java 包路径一致（运行期正确性，由 JniSymbolMirrorTest 锁）
    //   · native 物理目录**尽可能**镜像 Java 包路径，但受平台路径长度约束——
    //     cnn_active 曾是实例（W2 实测；W37 平铺后两侧已同路径，路径长度不再是问题）：Java 侧它住 mapper/cnn_active_mapper/nn/cnn_active/，
    //     native 侧若照搬会让 nvcc 报 "file name exceeds Windows path limit"，
    //     故 native 的 cnn_active 扁平住在 mapper/nn/cnn_active/（见 NATIVE_ABS_PATH_LIMIT）
    //   · 目录里不得留空包（实物与符号必须一致）
    // 注：Python 伪代码文档（S′）为历史遗留，不作为本次对齐依据。

    /** 位置事实：possession_sensor 系统包（W46 按概念归位到 sensor 概念之下）。 */
    public static final String P_SENSOR = "features/smarter/modes/sensor/possession_sensor";

    /**
     * 已达成 Java↔native 同路径的模块（两侧必须都存在该路径目录）。
     * <p>
     * 这些都是"路径深度尚可"的模块；{@code cnn_active} 因平台限制不在其列
     * （它只看 JNI 符号与 Java 包一致，物理目录扁平化）。
     */
    public static final List<String> MIRRORED_MODULE_PATHS = List.of("features/smarter/modes/nn/cnn_active",
            "features/smarter/modes/mapper/bnn_mapper",
            "features/smarter/modes/mapper/original_mapper",
            // W46 归位：nn 机制面成为 **nn 概念的内涵**（共享机制只被同概念的候选使用），
            // 与候选系统同栖于 modes/nn/（定理1(4) 手段③按概念分类；内涵/外延不可分离）
            "features/smarter/modes/nn",
            "features/smarter/modes/nn/cnn",
            "features/smarter/modes/nn/bnn",
            P_SENSOR + "/vision");

    /**
     * native 源文件<b>绝对路径</b>的字符数上限（平台约束，实测反推）。
     * <p>
     * 依据：Windows {@code MAX_PATH = 260}，nvcc 用 {@code GetLongPathName} 处理文件名，
     * 且编译器中间产物路径比源文件更长，故留余量取 250。
     * <p>
     * <b>为什么这条约束值得进清单</b>：W2 期间曾把 {@code cnn_active} 的 native 目录搬到
     * 镜像 Java 包路径的位置（{@code .../mapper/cnn_active_mapper/nn/cnn_active/}），
     * 结果 {@code gradlew cmakeBuild} 直接失败（相对路径 198 + 项目根 66 = 264 &gt; 260）。
     * 结论："目录镜像"存在<b>物理上限</b>——可读性必须让位于可构建性，这是"真"的一部分。
     */
    public static final int NATIVE_ABS_PATH_LIMIT = 250;
}
