package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.texture.Texture;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.execution.event.Event;
import com.github.magif1712.smarter_touhou_maids.core.execution.stream.Stream;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.ISensor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.vision.VisionOps;

/**
 * 附身感受器：唯一的具体 {@link ISensor} 实现，编排"去看"+"当眼睛"两个子模式。
 * <p>
 * 意识域 C（真善美第2条）：感受器 = agent 的"眼"，由两个并列子模式构成：
 * <ul>
 *   <li><b>vision（去看）</b>：{@link VisionOps} 把屏幕 OpenGL 纹理 → feelingBuffer（bit 平面编码），
 *       即"视网膜采集"。这是感受器的视觉采集子模式，藏在 {@code vision/} 子包。</li>
 *   <li><b>possession（当眼睛）</b>：玩家附身女仆，充当女仆的"眼睛"与区块加载器——
 *       没有玩家附身就没有屏幕画面可采，也就没有视觉输入。这是感受器的输入源子模式，
 *       藏在 {@code possession/} 子包（由 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.core.PossessionManager}
 *       管理附身状态，本类只编排采集，不直接碰附身逻辑）。</li>
 * </ul>
 * 两个子模式合起来才是完整的"眼"：possession 让玩家当眼睛（提供画面源），vision 去看（采集画面）。
 * 故二者同居 {@code possession_sensor/} 一个具体感受器子包下，是它的两个子模式。
 * <p>
 * <b>本类职责</b>：仅编排 vision 采集（包裹 {@link VisionOps}），把共享资源（feelingBuffer/cudaStream/
 * completionEvent）与捕获专用资源（快照纹理）接起来。possession 子模式的状态管理由外周
 * （PossessionManager / SmarterClientService 事件入口）负责，本类不感知——这是"possession 是感受器
 * 的模式"而非"感受器是 possession 的模式"的层级关系：possession 在 possession_sensor 内部。
 * <p>
 * <b>资源归属</b>：feelingBuffer/cudaStream/completionEvent 由 agent 注入（共享，agent 是 owner）；
 * 快照纹理由本类在 {@link #awaken} 自建、{@link #shutdown} 自释（捕获专用，隔离 OpenGL 源纹理竞态）。
 */
public class PossessionSensor implements ISensor {

    /** 由 agent 注入的共享感觉缓冲区（与 AI 共享）。 */
    private BoolVector feelingBuffer;
    /** 由 agent 注入的共享 CUDA 流（捕获 kernel 在其上异步提交）。 */
    private Stream cudaStream;
    /** 由 agent 注入的共享完成事件（capture 完成后 record，AI 跨流等待）。 */
    private Event completionEvent;

    /**
     * 池化的快照纹理（捕获专用资源）：glBlitFramebuffer 的缩放拷贝目标，
     * 隔离 OpenGL 修改源纹理时的竞态。由本类自建自释。
     */
    private Texture snapshotTexture;

    @Override
    public void awaken(BoolVector feelingBuffer, Stream cudaStream, Event completionEvent) {
        this.feelingBuffer = feelingBuffer;
        this.cudaStream = cudaStream;
        this.completionEvent = completionEvent;
        this.snapshotTexture = new Texture(VisionOps.AI_WIDTH, VisionOps.AI_HEIGHT);
    }

    @Override
    public void capture(int textureId, int texWidth, int texHeight) {
        VisionOps.captureScreenViaSnapshot(
                textureId,
                snapshotTexture,
                feelingBuffer,
                new Span(0, feelingBuffer.size()) {},
                texWidth, texHeight,
                cudaStream);
        // 视觉写完 feelingBuffer 后在 cudaStream 上 record，供 AI 流跨流等待。
        completionEvent.record(cudaStream.getHandle());
    }

    @Override
    public void shutdown() {
        if (snapshotTexture != null) {
            try { snapshotTexture.close(); } catch (Exception ignored) {}
            snapshotTexture = null;
        }
        // 共享引用（feelingBuffer/cudaStream/completionEvent）由 agent 释放，此处不处理。
        feelingBuffer = null;
        cudaStream = null;
        completionEvent = null;
    }
}
