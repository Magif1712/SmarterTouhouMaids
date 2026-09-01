package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.common;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.OutputVectorDomain;

/**
 * 锚点滑轨：两个 Anc 槽（沉淀物 / 悬浮物）组成的滑动窗口（照搬伪代码 {@code anc_slider.py}）。
 * <p>
 * tick() 反转两槽——悬浮物沉淀为沉淀物，新空位等待填充。
 * push 系列方法向指定槽写入感觉/行为向量区域。
 * <p>
 * 字段公开 outputDomain：照搬伪代码 {@code self.outputDomain} 的直接属性访问。
 * <p>
 * close() 真实释放两槽的 4 个向量（反序释放）；save/load 待设计落地。
 */
public class AncSlider implements AutoCloseable {

    public static final int PRECIPITATE = 0;
    public static final int SUSPENSION = 1;

    private final Anc[] pair;
    public final OutputVectorDomain outputDomain;

    public AncSlider(FittableMapper mapper, int feelingSize, int behaviorSize, OutputVectorDomain outputDomain) {
        this.pair = new Anc[]{
                new Anc(mapper.createVector(feelingSize), mapper.createVector(behaviorSize)),
                new Anc(mapper.createVector(feelingSize), mapper.createVector(behaviorSize))
        };
        this.outputDomain = outputDomain;
    }

    public Anc getPrecipitateAnc() {
        return pair[PRECIPITATE];
    }

    public Anc getSuspensionAnc() {
        return pair[SUSPENSION];
    }

    public void tick(/* <- */) {
        Anc tmp = pair[0];
        pair[0] = pair[1];
        pair[1] = tmp;
    }

    public void pushSuspensionFrom(/* <- */ VectorBase F, VectorBase output, long stream, OutputVectorDomain outputDomain) {
        Anc susp = getSuspensionAnc();
        susp.F.copyRegionFrom(/* <- */ F, fullSpan(F), fullSpan(susp.F), stream);
        susp.B.copyRegionFrom(/* <- */ output, outputDomain.getBehaviorSpan(), fullSpan(susp.B), stream);
    }

    public void pushSuspensionFromOutput(/* <- */ VectorBase source, long stream) {
        Anc susp = getSuspensionAnc();
        susp.F.copyRegionFrom(/* <- */ source, this.outputDomain.getFeelingSpan(), fullSpan(susp.F), stream);
        susp.B.copyRegionFrom(/* <- */ source, this.outputDomain.getBehaviorSpan(), fullSpan(susp.B), stream);
    }

    public void pushPrecipitateFrom(/* <- */ Anc anc, long stream) {
        Anc prec = getPrecipitateAnc();
        prec.F.copyRegionFrom(/* <- */ anc.F, fullSpan(anc.F), fullSpan(prec.F), stream);
        prec.B.copyRegionFrom(/* <- */ anc.B, fullSpan(anc.B), fullSpan(prec.B), stream);
    }

    // TODO 待 C 侧/设计落地：锚点滑轨的序列化（全窗口 4 槽：F/B × precipitate/suspension）
    public void save(String uranaPath, String sliderId) {
    }

    public void load(String uranaPath, String sliderId) {
    }

    @Override
    public void close() {
        // 反序释放
        if (pair[1] != null) {
            if (pair[1].F != null) pair[1].F.close();
            if (pair[1].B != null) pair[1].B.close();
        }
        if (pair[0] != null) {
            if (pair[0].F != null) pair[0].F.close();
            if (pair[0].B != null) pair[0].B.close();
        }
    }

    private static Span fullSpan(VectorBase v) {
        return new Span(0, v.size()) {};
    }
}
