package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 单个参数项：一个 agent 暴露给 GUI 的可配置项。
 * <p>
 * 与 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug.DebugOption} 对称：
 * DebugOption 是 final class + onOff 工厂 + getter/setter lambda；
 * ParamOption 是 final class + of/persistable 工厂 + currentText/commitText lambda。
 * 两者都通过 lambda 参数化实现行为多样性，不需要接口多态（无多模态——参数项的所有多样性
 * 由 lambda + hint/meta 槽位覆盖，不存在"结构不同的参数项"这种可切换模态）。
 * <p>
 * <b>纯 text 透传</b>（真善美第4条）：管道只搬运 String，不感知值类型。
 * {@link #commitText} / {@link #currentText} 是 text 入口——UI 只调这两个方法，
 * 解读（text → 值）+ 约束（如 clamp）由 factory 在 textProcessor / {@link ParamUtils} 中负责。
 * <p>
 * <b>交互提示槽位</b>（真善美第2条）：意识中参数项带"最自然的交互方式"这个属性——
 * 它是参数的属性，不是参数的种类（不存在"布尔参数/文本参数"的分类，C 中无则 D 中也不造特化类）。
 * D 中用 {@link #controlHint}（控件种类）+ {@link #controlMeta}（控件参数）两个槽位如实体现。
 * 两者与 {@link #label}/{@link #tooltip} 同性质——都是参数项的<b>静态展示属性</b>，
 * GUI 读取，<b>不进 ParamStore、不进网络包</b>。管道层（ParamStore/网络包）不解读 hint/meta。
 * <p>
 * <b>三层互不越界</b>（真善美第3条）：
 * <ul>
 *   <li>管道层（本类 + ParamStore + 网络包）：搬 text + 透传 hint/meta 字符串，不解读含义。</li>
 *   <li>factory 消费层（{@link #of}/{@link #persistable} + {@link ParamUtils}）：声明参数 + 填 hint/meta。</li>
 *   <li>config_gui 层（RuntimeParamsPanel 等）：解读 hint → 选控件，读 meta 配置控件。</li>
 * </ul>
 * factory 填新 hint 值（Y1=toggle → Y2=slider）时，本类、ParamStore、网络包零改动——
 * 只有 config_gui 层可能新增 case，那是 config_gui 自己的事。
 * <p>
 * <b>不可变 + 链式</b>（真善美第4条）：把"交互提示"这个不实在的概念，实在化为 final 字段 +
 * {@link #withControlHint}/{@link #withControlMeta} 返回新实例。构建期链式调用，构建完成后只读。
 * <p>
 * <b>hint 值集合不由本类规定</b>（简政放权）：standard_config_gui 自己定义认识的 hint，
 * 附属 config_gui 可定义不同集合。未知 hint 降级为 EditBox 兜底。
 * <p>
 * <b>行为多样性通过 lambda 参数化</b>（真善美第3条）：{@link #of} 接收任意 currentText/commitText
 * lambda——附属想要任何存储模型（自己的 NBT、自己的网络包、动态计算）都通过传不同 lambda 实现，
 * 不需要自实现类。与 DebugOption 通过 onOff 接收 getter/setter lambda 同构。
 * <p>
 * <b>通用工厂方法</b>（真善美第4条）：
 * <ul>
 *   <li>{@link #of}：自备 currentText/commitText lambda（factory 自管持久化）。可链式 with。</li>
 *   <li>{@link #persistable}：委托 ParamStore，可选 textProcessor（commit 时预处理 text）。可链式 with。</li>
 * </ul>
 */
public final class ParamOption {
    private final Component label;
    private final Component tooltip;
    private final Function<EntityMaid, String> currentTextFn;
    private final BiConsumer<EntityMaid, String> commitTextFn;
    private final String controlHint;
    private final Map<String, String> controlMeta;

    private ParamOption(Component label, Component tooltip,
                        Function<EntityMaid, String> currentTextFn,
                        BiConsumer<EntityMaid, String> commitTextFn,
                        String controlHint, Map<String, String> controlMeta) {
        this.label = label;
        this.tooltip = tooltip;
        this.currentTextFn = currentTextFn;
        this.commitTextFn = commitTextFn;
        this.controlHint = controlHint;
        this.controlMeta = controlMeta == null ? Map.of() : Map.copyOf(controlMeta);
    }

    /** 参数项标签（i18n）。 */
    public Component label() {
        return label;
    }

    /** 参数项 tooltip（i18n）。 */
    public Component tooltip() {
        return tooltip;
    }

    /**
     * 将玩家输入的文本提交到管道（ParamStore 持久化 + 同步）。
     * <p>
     * 解读（parse）+ 约束（clamp）由 factory 在 textProcessor 中负责，管道不感知值类型。
     *
     * @param maid 目标女仆
     * @param text 玩家输入的原始文本
     */
    public void commitText(EntityMaid maid, String text) {
        commitTextFn.accept(maid, text);
    }

    /**
     * 当前值的文本表示（供 UI 回显）。
     *
     * @param maid 目标女仆
     * @return 管道中存储的 String 值
     */
    public String currentText(EntityMaid maid) {
        return currentTextFn.apply(maid);
    }

    /**
     * 交互提示：factory 建议的控件种类。管道不解读，config_gui 解读。
     * <p>
     * 是参数项的静态展示属性（与 {@link #label}/{@link #tooltip} 同性质），不进 ParamStore、不进网络包。
     * 默认 "text" → EditBox（普适兜底）。常见值如 "toggle"/"slider"/"long_text"，
     * 具体值集合由各 config_gui 实现自己规定（简政放权），未知值降级 EditBox。
     *
     * @return 控件种类提示字符串
     */
    public String controlHint() {
        return controlHint;
    }

    /**
     * 交互元数据：控件参数（如 slider 的 min/max/step、long_text 的行数）。
     * <p>
     * 与 {@link #controlHint} 同属"交互方式"属性，管道不解读，config_gui 解读。
     * 默认空 Map。是普适 {@code Map<String, String>} 容器——不为每种元数据加专门接口方法（简政放权）。
     *
     * @return 控件参数的不可变 Map
     */
    public Map<String, String> controlMeta() {
        return controlMeta;
    }

    /**
     * 自备 currentText/commitText 的通用工厂（factory 自管持久化时用）。
     * <p>
     * 返回 {@link ParamOption}，可链式 {@code .withControlHint(...).withControlMeta(...)}。
     *
     * @param currentTextFn 当前值的 text 表示（maid → String）
     * @param commitTextFn  提交 text（maid + text → 持久化）
     */
    public static ParamOption of(Component label, Component tooltip,
                                 Function<EntityMaid, String> currentTextFn,
                                 BiConsumer<EntityMaid, String> commitTextFn) {
        return new ParamOption(label, tooltip, currentTextFn, commitTextFn, "text", Map.of());
    }

    /**
     * 持久化参数工厂（委托 ParamStore），带 textProcessor。
     * <p>
     * textProcessor：commit 时预处理 text（parse/clamp/normalize），返回要存入 ParamStore 的 String。
     * factory 在此提供值类型解读逻辑——管道只调 textProcessor，不感知值类型。
     * currentText 直接返回 ParamStore 中的 String（已处理的值）。
     * <p>
     * 返回 {@link ParamOption}，可链式 {@code .withControlHint(...).withControlMeta(...)}。
     *
     * @param nbtKey        参数在 maid modData 下的 NBT key
     * @param defaultValue  未设置时的默认值（String 形式）
     * @param textProcessor commit 时的 text 预处理（parse/clamp/normalize）
     */
    public static ParamOption persistable(Component label, Component tooltip,
                                          String nbtKey, String defaultValue,
                                          BiFunction<EntityMaid, String, String> textProcessor) {
        return of(label, tooltip,
                maid -> ParamStore.INSTANCE.getString(maid, nbtKey, defaultValue),
                (maid, text) -> ParamStore.INSTANCE.setString(maid, nbtKey, textProcessor.apply(maid, text)));
    }

    /**
     * 持久化参数工厂（委托 ParamStore），无 textProcessor。
     * 等价于 {@link #persistable(Component, Component, String, String, BiFunction)
     * persistable(…, (maid, text) -> text)}。适用于 string 参数（直接存，无需 parse）。
     */
    public static ParamOption persistable(Component label, Component tooltip,
                                          String nbtKey, String defaultValue) {
        return persistable(label, tooltip, nbtKey, defaultValue, (maid, text) -> text);
    }

    /**
     * 链式设置控件提示，返回新实例（不可变）。
     * <p>
     * factory 典型用法：
     * <pre>{@code
     * ParamOption.of(label, tooltip, currentFn, commitFn)
     *     .withControlHint("toggle");
     * }</pre>
     *
     * @param hint 控件种类提示（如 "text"/"toggle"/"slider"/"long_text"），管道不解读，config_gui 解读
     */
    public ParamOption withControlHint(String hint) {
        return new ParamOption(label, tooltip, currentTextFn, commitTextFn, hint, controlMeta);
    }

    /**
     * 链式设置控件元数据，返回新实例（不可变）。
     * <p>
     * factory 典型用法：
     * <pre>{@code
     * ParamOption.persistable(label, tooltip, nbtKey, default, processor)
     *     .withControlHint("slider")
     *     .withControlMeta(Map.of("min", "0", "max", "1", "step", "0.01"));
     * }</pre>
     *
     * @param meta 控件参数（如 slider 的 min/max/step），管道不解读，config_gui 解读
     */
    public ParamOption withControlMeta(Map<String, String> meta) {
        return new ParamOption(label, tooltip, currentTextFn, commitTextFn, controlHint, meta);
    }
}
