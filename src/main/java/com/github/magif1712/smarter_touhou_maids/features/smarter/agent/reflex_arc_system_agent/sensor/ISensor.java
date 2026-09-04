package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.core.execution.RefreshRequest;
import com.github.magif1712.smarter_touhou_maids.core.execution.event.Event;
import com.github.magif1712.smarter_touhou_maids.core.execution.stream.Stream;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.VisionEncoder;

/**
 * 感受器的机械级抽象边界（agent 的"眼"）。
 * <p>
 * 镜像 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.IAiSystem}
 * （思考机制契约）与 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.INeuralNetwork}
 * （nn 契约）：上层 agent 持本接口引用，感受器实现可替换，换感受器时 agent 零改动。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>第2条</b>：agent 意识域 C 只有"感知→思考→行动"的感知环节，不含视觉采集细节
 *      （glBlitFramebuffer/bit 平面提取）、不含附身细节（玩家当眼睛/区块加载）。
 *       这些感受器内部模式必须藏在实现里，agent 通过本接口访问。</li>
 *   <li><b>第3条</b>：把"可替换感受器"这个不实在的约束，用实在的接口（有签名的方法）固化。</li>
 * </ul>
 * <p>
 * <b>资源归属</b>：feelingBuffer/cudaStream/completionEvent 由 agent 创建并注入（agent 是这些共享资源的 owner，
 * AI 也共用它们），感受器只持引用。捕获专用资源（如快照纹理）由感受器在 {@link #awaken} 自建、{@link #shutdown} 自释。
 *
 * @see com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.PossessionSensor
 */
public interface ISensor {

    /**
     * 接收共享引用 + 创建捕获资源。
     * <p>
     * 由 agent 在 awaken 时调用：把与 AI 共享的 feelingBuffer/cudaStream/completionEvent 注入感受器，
     * 感受器据此自建捕获专用资源（如快照纹理）。
     * <p>
     * feelingBuffer 类型为 {@link VectorBase}（与 {@code IProcessSystem.awaken} 对称）：
     * 感受器编码是 agent 分支的私有模式（位平面 BoolVector / RGB float FloatVector），
     * 具体实现内部向下转型为自己的编码容器。
     *
     * @param feelingBuffer   感觉缓冲区（与 AI 共享，感受器写入、AI 读取；编码由具体感受器决定）。
     * @param cudaStream      CUDA 流（感受器在 其上异步提交捕获 kernel，与 AI 专用流并发）。
     * @param completionEvent 捕获完成事件（感受器 capture 完成后 record，AI 每轮开头 waitEvent 跨流等待）。
     */
    void awaken(VectorBase feelingBuffer, Stream cudaStream, Event completionEvent);

    /**
     * 注入感觉刷新请求（拉模型，可选能力）。
     * <p>
     * 支持按需编码的感受器 Override 本方法存下请求：AI 每轮 request，感受器 consume 后才编码
     * （编码次数 ≤ 消费次数）。不支持的感受器走默认实现（忽略请求，保持每帧编码的推模型）——
     * 契约向后兼容，旧实现零改动。agent 在 awaken 后调用。
     *
     * @param feelingRefresh 感觉刷新请求（agent 拥有，纯 host 对象）。
     */
    default void setRefreshRequest(RefreshRequest feelingRefresh) {
        // 默认无操作：不支持拉模型的感受器忽略刷新请求
    }

    /**
     * 注入视觉解码器（fittable_mapper 层契约，可选能力）。
     * <p>
     * 支持可插拔解码的感受器 Override 本方法存下解码器：采集（快照）后按需调用
     * {@code encoder.encode} 把快照纹理解码进 feelingBuffer。解码器由 agent 从 ai 链取得注入
     * （nn 家族定义感觉载体者同时提供解码器——非法组合结构上不可表达，无类型开关）。
     * 不支持的感受器走默认实现（忽略，保持自带编码）——契约向后兼容，旧实现零改动。
     * agent 在 awaken + setRefreshRequest 之后调用（实现可据此时机做装配期校验）。
     *
     * @param visionEncoder 视觉解码器（来自 ai 链的 nn 家族贡献，fittable_mapper 层契约）。
     */
    default void setVisionEncoder(VisionEncoder visionEncoder) {
        // 默认无操作：不支持可插拔解码的感受器忽略解码器（编码自理，如原初融合路径）
    }

    /**
     * 感知一帧 → feelingBuffer（在 cudaStream 上异步），完成后 record completionEvent。
     * <p>
     * 由 agent 在 onPostRender（渲染线程，拥有 OpenGL 上下文）调用。快照每帧执行（保鲜）；
     * 编码是否执行由实现自决（如据注入的刷新请求按需编码）——"感知一帧"的内部编排
     * 属于感受器私有模式，不进本契约。
     *
     * @param textureId 源 OpenGL 纹理 ID（当前帧画面）。
     * @param texWidth  源纹理实际宽度（像素）。
     * @param texHeight 源纹理实际高度（像素）。
     */
    void capture(int textureId, int texWidth, int texHeight);

    /**
     * 释放捕获专用资源（如快照纹理）。共享引用（feelingBuffer/cudaStream/completionEvent）由 agent 释放，不在此处理。
     */
    void shutdown();
}
