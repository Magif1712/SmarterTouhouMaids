package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.execution.event.Event;
import com.github.magif1712.smarter_touhou_maids.core.execution.stream.Stream;

/**
 * 感受器的机械级抽象边界（agent 的"眼"）。
 * <p>
 * 镜像 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.IAiSystem}
 * （思考机制契约）与 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.INeuralNetwork}
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
     *
     * @param feelingBuffer   感觉缓冲区（与 AI 共享，感受器写入、AI 读取）。
     * @param cudaStream      CUDA 流（感受器在 其上异步提交捕获 kernel，与 AI 专用流并发）。
     * @param completionEvent 捕获完成事件（感受器 capture 完成后 record，AI 每轮开头 waitEvent 跨流等待）。
     */
    void awaken(BoolVector feelingBuffer, Stream cudaStream, Event completionEvent);

    /**
     * 感知一帧 → feelingBuffer（在 cudaStream 上异步），完成后 record completionEvent。
     * <p>
     * 由 agent 在 onPostRender（渲染线程，拥有 OpenGL 上下文）调用。
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
