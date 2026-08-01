package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.core.execution.event.Event;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.IAiSystem;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.IProcessSystem;
import com.github.magif1712.smarter_touhou_maids.core.execution.MappedGenerationBuffer;

/**
 * 流程型 ai 的实现（过程哲学路线：世界是过程的集合体，而非即成事物的集合体）。
 * <p>
 * process 是本体——意识的流变；nn 是 process 借以运算的机制。urana 这个流程系统内部
 * 用一个可替换的神经网络（BNN/CNN/...）做计算，故 nn 住在 urana_process 内部，不与 process 平级。
 * 本类只直接依赖 {@link IProcessSystem}（流程系统抽象），不感知 nn——换 nn 是 urana 内部的事，
 * 换 process 才是本类的事。
 * <p>
 * <b>注入模式（镜像 {@code UranaSystem}）</b>：UranaSystem 直接用的是 nn，就构造注入
 * {@code INeuralNetwork}，换 nn（BNN→CNN）时 UranaSystem 零改动；本类直接用的是 process，
 * 就构造注入 {@link IProcessSystem}，换 process（urana→别的）时本类零改动。每个类只注入自己
 * 直接使用的那个抽象（真善美第1条"真"：不引入自己用不到的依赖——本类不直接用 nn，故不持 nn）。
 * <p>
 * <b>组装职责</b>：本类只有注入构造函数，无便利构造函数——组装（选哪个 process、其内部用哪个 nn）
 * 由 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.ProcessAiFactory}
 * 在外周从 registry 查 factory 自驱完成（配置驱动，非硬编码）。上层只需做到"下层换模式也不改代码
 * 就能正确运行"，不需要在进入上层之前把下层"便利"起来。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：C 中"process 型 ai = 一个流程系统"是这种 ai 的模式。流程系统内部的 nn、
 *       子系统、anchor/gradcell、span 语义等都是 urana 这个具体流程的内部模式，不进本类。
 *       本类只承载"ai = process"这一层模式。</li>
 *   <li><b>第3条</b>：把"可替换流程系统"这个不实在的约束，实在化为构造注入——本类持
 *       {@link IProcessSystem} 接口引用，由外周传入具体流程系统。</li>
 * </ul>
 * <p>
 * <b>与 IAiSystem 的关系</b>：本类是 {@link IAiSystem} 的一种具体实现（流程路线）。
 * 附属模组作者若要实现非流程范式的 ai（纯规则、统计分类等），直接实现 {@link IAiSystem} 即可，
 * 不需继承本类，也不需碰 process_ai/process/ 包。
 * <p>
 * <b>委托模式</b>：本类所有 {@link IAiSystem} 方法委托给内部 {@link IProcessSystem}。
 * 看似与 IProcessSystem 冗余，但语义层次不同——IAiSystem 是所有流派 ai 的顶层契约，
 * IProcessSystem 是 process_ai 型 ai 内部 process 组件的契约。纯规则 ai 不涉及 IProcessSystem，
 * 直接实现 IAiSystem，故 IAiSystem 不是 IProcessSystem 的冗余副本。
 * <p>
 * <b>生命周期</b>：nn 的 save/close 由 process（UranaSystem）内部负责（process 持有并使用 nn）。
 * 本类 {@link #close()} 委托 {@code process.close()}，不重复关闭 nn——避免双重释放。
 */
public class ProcessAiSystem implements IAiSystem {

    private final IProcessSystem process;

    /**
     * 注入构造函数（镜像 {@link UranaSystem} 的注入模式）：直接注入本类使用的流程系统抽象。
     * 换流程系统时本类零改动。
     * <p>
     * 调用方负责构造具体 process（含其内部 nn 的组装）。本类不感知 process 内部用了什么 nn。
     *
     * @param process 流程系统抽象（如 UranaSystem，内部已注入 nn）。
     */
    public ProcessAiSystem(IProcessSystem process) {
        this.process = process;
    }

    @Override
    public void awaken(VectorBase feelingBuffer, Event visionEvent, MappedGenerationBuffer behaviorChannel) {
        process.awaken(feelingBuffer, visionEvent, behaviorChannel);
    }

    @Override
    public void shutdown() {
        process.shutdown();
    }

    @Override
    public void save(String folderPath) {
        process.save(folderPath);
    }

    @Override
    public void setDtDebugEnabled(boolean enabled) {
        process.setDtDebugEnabled(enabled);
    }

    @Override
    public int feelingSize() {
        return process.feelingSize();
    }

    @Override
    public int behaviorSize() {
        return process.behaviorSize();
    }

    @Override
    public void close() throws Exception {
        process.close();
    }
}
