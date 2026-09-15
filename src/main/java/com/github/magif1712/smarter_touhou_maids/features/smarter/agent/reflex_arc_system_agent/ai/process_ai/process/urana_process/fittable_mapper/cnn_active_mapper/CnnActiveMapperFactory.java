package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamOption;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamPanelProvider;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamStore;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapperFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper.nn.cnn_active.CnnActiveNnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper.nn.cnn_active.CnnActiveOptions;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnEncodingProfile;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.NnFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.IODomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.InputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.OutputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.AssemblyContext;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.OutSlot;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * 新活性 CNN 的映射器工厂（叶子）：照抄 {@code OriginalMapperFactory}，
 * 并额外实现 {@link ParamPanelProvider}——把分解方式 {@code D} 上推到 GUI（方案 §五 裁决1、§八）。
 * <p>
 * <b>为什么 D 挂在 mapper factory 而非 nn 上</b>（方案 §八）：{@code D} 表达的是
 * "这次选哪套分解方式"这个<b>上层决策</b>。nn 工厂的 {@code create(SaveSlot,int,int)} 签名里
 * 没有 maid 上下文，拿不到 per-maid 参数；而 mapper 工厂的 {@code create(AssemblyContext,...)}
 * 有 {@code ctx.maid()}。把 D 放在"有上下文的那一层"，是它唯一能真正生效的位置。
 * <p>
 * <b>组装流程</b>：读 D（per-maid，经 {@link ParamStore}）→ 问 nn provider 要 profile → 建 IODomain 算尺寸
 * → 带 D 实例化 nn（load 或对称初始化）→ 装 mapper。
 * <p>
 * <b>{@code Ext(D)} 的结构性裁剪</b>（推论2，方案 §五 裁决2）：GUI 只暴露
 * {@code activationId ∈ {1,2}} 与两个 {@code bound > 0}；{@code sigmoid}/{@code threshold}/
 * {@code p} 的符号域<b>不存在</b>于选择空间，故"σ × 全非负初始化"与"σ × 有符号 target"
 * 这两类病态组合在结构上不可能被选出。
 */
public class CnnActiveMapperFactory implements FittableMapperFactory, ParamPanelProvider {

    private static final String KEY_ACTIVATION_ID = "CnnActiveActivationId";
    private static final String KEY_INIT_BOUND_W = "CnnActiveInitBoundW";
    private static final String KEY_INIT_BOUND_B = "CnnActiveInitBoundB";

    /**
     * 初始化半径的 slider 范围。下限 {@code > 0} 是硬约束（{@link CnnActiveOptions} 构造期 fail-fast）：
     * clamp 到本范围即保证"解析出的值必然合法"，非法文本不可能进入运行期。
     * <p>
     * <b>上限 4.0 的重新定性（L0.4，方案 §5.3）</b>：本注释原先写"上限不设技术上必须的界线，只是实测调参的
     * 搜索边界"，但那在 D4 引入 B 段自环之后<b>已与事实矛盾</b>——初始 {@code l,r ~ U(−bound, bound)}
     * ⟹ 初始自环增益 {@code |l|+|r| ≤ 2·bound}，{@code bound = 4.0} 时可达 8（8 倍超临界，开局即不稳）。
     * 现按方案 §5.3 <b>选项乙</b>定性：<b>本 slider 管"初始化的探索空间"，运行期的可行集由不变量管</b>
     * ——{@code I3}（权重限幅 {@code |w| ≤ 4}）与 {@code I4}（B 段自环收缩 {@code |l_j|+|r_j| ≤ 0.9}，
     * 见 native {@code gradient_ops.cu}）会在第一次反向步就把超临界的初始样本投影回可行集
     * （**已实证**：B 段增益被钳到恰 {@code 0.9000}）。故 {@code 4.0} 仍是**可探索**的上限，
     * 但不再声称"技术上不必设界"——"不设界"与"实际是技术界线"并存就是不真（真善美第1条）。
     */
    private static final float MIN_BOUND = 0.05f;
    private static final float MAX_BOUND = 4.0f;

    /**
     * 树原生组装（覆盖 {@link FittableMapperFactory} 的 default）：
     * 与 default 的唯一差异是<b>插入 D 的读取与传递</b>（default 没有 maid 上下文）。
     */
    @Override
    public void create(AssemblyContext ctx, /*->*/ OutSlot<FittableMapper> out) {
        // === 裁决1 的落点：D 从 GUI 上推为显式入参（per-maid，经 ParamStore）===
        CnnActiveOptions options = readOptions(ctx.maid());

        NnFactory nnProvider = ctx.child("nn", NnFactory.class);
        NnEncodingProfile profile = nnProvider.encodingProfile();
        IODomain ioDomain = new IODomain(profile);
        int inLen = ioDomain.getInputDomain().totalLength();
        int outLen = ioDomain.getOutputDomain().totalLength();

        // === 带 D 实例化 nn（load 到磁盘权重则保留符号域，否则全新对称初始化）===
        INeuralNetwork nn = CnnActiveNnFactory.createConfigured(nnProvider, ctx.saveSlot(), inLen, outLen, options);

        out.set(createMapped(nn, ioDomain.getInputDomain(), ioDomain.getOutputDomain()));
    }

    @Override
    public FittableMapper createMapped(INeuralNetwork nn, InputVectorDomain inputDomain, OutputVectorDomain outputDomain) {
        return new CnnActiveMapper(nn, inputDomain, outputDomain);
    }

    /**
     * 暴露分解方式 {@code D} 的三项可调参数（per-maid，随存档走）。
     * <p>
     * {@code create()}/{@code readOptions()} 与本方法共用同一组 nbtKey + 默认值 + 解析器——单一数据源。
     * {@code textProcessor} 在 commit 时 parse + clamp + {@code String.valueOf}：
     * 管道（{@link ParamStore}）只搬 String，不感知 int/float（真善美第3条）。
     */
    @Override
    public List<ParamOption> getParamOptions() {
        return List.of(
                ParamOption.persistable(
                        Component.translatable("option.smarter_touhou_maids.cnn_active_activation"),
                        Component.translatable("option.smarter_touhou_maids.cnn_active_activation.tooltip"),
                        KEY_ACTIVATION_ID, String.valueOf(CnnActiveOptions.ACTIVATION_TANH),
                        (maid, text) -> String.valueOf(parseActivationId(text)))
                        .withControlHint("slider")
                        .withControlMeta(Map.of("min", "1", "max", "2", "step", "1")),
                ParamOption.persistable(
                        Component.translatable("option.smarter_touhou_maids.cnn_active_init_bound_w"),
                        Component.translatable("option.smarter_touhou_maids.cnn_active_init_bound_w.tooltip"),
                        KEY_INIT_BOUND_W, String.valueOf(CnnActiveOptions.DEFAULT_BOUND),
                        (maid, text) -> String.valueOf(parseBound(text)))
                        .withControlHint("slider")
                        .withControlMeta(Map.of("min", String.valueOf(MIN_BOUND), "max", String.valueOf(MAX_BOUND), "step", "0.05")),
                ParamOption.persistable(
                        Component.translatable("option.smarter_touhou_maids.cnn_active_init_bound_b"),
                        Component.translatable("option.smarter_touhou_maids.cnn_active_init_bound_b.tooltip"),
                        KEY_INIT_BOUND_B, String.valueOf(CnnActiveOptions.DEFAULT_BOUND),
                        (maid, text) -> String.valueOf(parseBound(text)))
                        .withControlHint("slider")
                        .withControlMeta(Map.of("min", String.valueOf(MIN_BOUND), "max", String.valueOf(MAX_BOUND), "step", "0.05")));
    }

    // ==================== D 的读取与解析（create 与 getParamOptions 共用，单一数据源）====================

    /** 从 per-maid 存储读取 D；maid 为 null 时 ParamStore 返回默认值（首次组装前即可配置）。 */
    private static CnnActiveOptions readOptions(EntityMaid maid) {
        int activationId = parseActivationId(ParamStore.INSTANCE.getString(
                maid, KEY_ACTIVATION_ID, String.valueOf(CnnActiveOptions.ACTIVATION_TANH)));
        float boundW = parseBound(ParamStore.INSTANCE.getString(
                maid, KEY_INIT_BOUND_W, String.valueOf(CnnActiveOptions.DEFAULT_BOUND)));
        float boundB = parseBound(ParamStore.INSTANCE.getString(
                maid, KEY_INIT_BOUND_B, String.valueOf(CnnActiveOptions.DEFAULT_BOUND)));
        return new CnnActiveOptions(activationId, boundW, boundB);
    }

    /**
     * 解析激活 id：只接受 {@link CnnActiveOptions#ACTIVATION_TANH} 与
     * {@link CnnActiveOptions#ACTIVATION_CLIPPED_LINEAR}；其余（含 {@code 0}/非法文本/越界）
     * 一律回退 tanh。
     * <p>
     * 回退而非抛异常：GUI 输入是外部数据，非法输入应被"收敛到合法值"而不是让组装崩溃——
     * 且回退目标是<b>默认推荐的合法项</b>，绝不可能是 sigmoid（它不在 {@code Ext(D)} 内）。
     */
    private static int parseActivationId(String text) {
        try {
            int v = Integer.parseInt(text.trim());
            return (v == CnnActiveOptions.ACTIVATION_CLIPPED_LINEAR)
                    ? CnnActiveOptions.ACTIVATION_CLIPPED_LINEAR
                    : CnnActiveOptions.ACTIVATION_TANH;
        } catch (NumberFormatException e) {
            return CnnActiveOptions.ACTIVATION_TANH;
        }
    }

    /**
     * 解析初始化半径：parse → clamp 到 {@code [MIN_BOUND, MAX_BOUND]}（必然 {@code > 0}，
     * 满足 {@link CnnActiveOptions} 的构造期约束）→ 失败回退默认半径。
     */
    private static float parseBound(String text) {
        try {
            float v = Float.parseFloat(text.trim());
            if (!(v > 0.0f) || !Float.isFinite(v)) {
                return CnnActiveOptions.DEFAULT_BOUND;
            }
            return Math.max(MIN_BOUND, Math.min(MAX_BOUND, v));
        } catch (NumberFormatException e) {
            return CnnActiveOptions.DEFAULT_BOUND;
        }
    }
}
