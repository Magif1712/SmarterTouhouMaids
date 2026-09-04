package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.texture.Texture;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.core.execution.stream.Stream;

/**
 * 视觉解码器：把快照纹理（GL 纹理）解码为特定 nn 家族可利用的载体数据
 * （fittable_mapper 层的契约——本层"特地为 Texture 解码输出为 NN 可利用数据类型而存在"）。
 * <p>
 * <b>契约住层根，实现住家族包</b>（真善美第2条）：解码是 fittable_mapper 层的模式
 * （区别于 sensor 层的采集原语 glBlit），契约定义在本层根（与 {@link FittableMapper} 同级），
 * 供 nn 层契约（INeuralNetwork.newVisionEncoder）与感受器（ISensor.setVisionEncoder）
 * 共同引用；各家族实现住自己的家族包（如 original_mapper 的位平面/RGB float 解码器）。
 * <p>
 * <b>配对原则</b>：解码器与其目标载体由 nn 家族同时定义（BNN→BoolVector 位平面，
 * CNN→FloatVector RGB float），经 ai 链（nn→mapper→process→ai）上浮、由 agent 注入感受器——
 * 定义载体者同时提供解码器，非法组合结构上不可表达（无类型开关、无平行布尔量）。
 * <p>
 * <b>采集与解码分离</b>：采集（glBlit 深拷贝快照，sensor 域稳定原语）每帧执行保鲜；
 * 解码经由快照纹理（GL 纹理）插入 OpenGL 命令序列（GL 单一命令流 = 渲染流，与渲染串行）。
 * 解码由 AI 消费挂起（拉模型，{@code RefreshRequest}）：频率与 AI 匹配，永不超出实际需求。
 */
public interface VisionEncoder {

    /**
     * 从快照纹理解码到输出缓冲区（在 CUDA stream 上异步执行，CPU 侧立即返回）。
     * <p>
     * 载体类型知识在实现内部（instanceof 校验 fail-fast），调用方只见 {@link VectorBase}。
     *
     * <h3>DPS</h3>
     * 入参：快照纹理 + 写入区间 + CUDA流<br>
     * 出参：output<br>
     * 数据流：snapshotTexture[cuArray] → Map → kernel → Unmap → output[span]
     *
     * @param snapshotTexture 快照纹理（已由采集原语 glBlit 深拷贝填充的独立副本，尺寸即解码区域）。
     * @param destSpan        定义 output 中的写入区间（载体单位：BoolVector=bit，FloatVector=元素）。
     * @param stream          CUDA 流（kernel 在其上异步提交）。
     * @param output          目标缓冲区（解码后的数据；具体容器类型由实现决定，如 BoolVector/FloatVector）。
     */
    void encode(Texture snapshotTexture, Span destSpan, Stream stream, /*->*/ VectorBase output);

    /**
     * 解码器所需输出缓冲区的最小载体单位数（供装配期校验解码器与缓冲匹配）。
     * <p>
     * 单位随载体：BoolVector 按 bit 计（w*h*24），FloatVector 按元素计（w*h*3）——
     * 与对应载体 size() 的单位一致。
     */
    long requiredUnits(int aiWidth, int aiHeight);

    /**
     * 释放解码器持有的原生资源（无持久资源者空实现）。
     */
    void close();
}
