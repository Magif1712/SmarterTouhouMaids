package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.texture.Texture;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.core.execution.RefreshRequest;
import com.github.magif1712.smarter_touhou_maids.core.execution.event.Event;
import com.github.magif1712.smarter_touhou_maids.core.execution.stream.Stream;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.VisionEncoder;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.ISensor;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.vision.VisionOps;

/**
 * 附身感受器：唯一的具体 {@link ISensor} 实现，编排"去看"+"当眼睛"两个子模式。
 * <p>
 * 意识域 C（真善美第2条）：感受器 = agent 的"眼"，由两个并列子模式构成：
 * <ul>
 *   <li><b>vision（去看）</b>：快照（{@link VisionOps#snapshot}，glBlit 深拷贝）+ 按需解码
 *       （{@link VisionEncoder}，可插拔，住 fittable_mapper 层），即"视网膜采集与解码"。
 *       采集是感受器视觉子模式的稳定原语，藏在 {@code vision/} 子包。</li>
 *   <li><b>possession（当眼睛）</b>：玩家附身女仆，充当女仆的"眼睛"与区块加载器——
 *       没有玩家附身就没有屏幕画面可采，也就没有视觉输入。这是感受器的输入源子模式，
 *       藏在 {@code possession/} 子包（由 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.core.PossessionManager}
 *       管理附身状态，本类只编排采集，不直接碰附身逻辑）。</li>
 * </ul>
 * 两个子模式合起来才是完整的"眼"：possession 让玩家当眼睛（提供画面源），vision 去看（采集画面）。
 * 故二者同居 {@code possession_sensor/} 一个具体感受器子包下，是它的两个子模式。
 * <p>
 * <b>采集与解码分离</b>：采集（~0.1ms）每帧执行保鲜；解码经由快照纹理（GL 纹理），
 * 操作插入 OpenGL 命令序列（GL 单一命令流 = 渲染流，与渲染串行）。解码由 AI 消费
 * 挂起（拉模型 {@link RefreshRequest}）：AI 每轮 request，本感受器 consume 后才解码——
 * 解码次数 ≤ 消费次数，频率与 AI 匹配，降低渲染流的算力占用。
 * <p>
 * <b>本类职责</b>：仅编排 vision 采集 + 按需解码，把共享资源（feelingBuffer/cudaStream/
 * completionEvent/feelingRefresh）与捕获专用资源（快照纹理）接起来。possession 子模式的
 * 状态管理由外周（PossessionManager / SmarterClientService 事件入口）负责，本类不感知——
 * 这是"possession 是感受器的模式"而非"感受器是 possession 的模式"的层级关系：
 * possession 在 possession_sensor 内部。
 * <p>
 * <b>资源归属</b>：feelingBuffer/cudaStream/completionEvent/feelingRefresh 由 agent 注入
 * （共享，agent 是 owner）；解码器由 agent 从 ai 链取得注入（nn 家族定义感觉载体者同时
 * 提供解码器——与 feelingBuffer 载体天然配对，非法组合结构上不可表达）；快照纹理由本类在
 * {@link #awaken} 自建、{@link #shutdown} 自释（捕获专用，隔离 OpenGL 源纹理竞态）。
 */
public class PossessionSensor implements ISensor {

    /**
     * 视觉解码器（fittable_mapper 层契约，agent 从 ai 链取得注入）。
     * 载体类型知识（BoolVector/FloatVector）在解码器内部，本类不感知。
     */
    private VisionEncoder encoder;
    /** 由 agent 注入的共享感觉缓冲区（与 AI 共享；载体类型由 ai 链的 nn 家族决定）。 */
    private VectorBase feelingBuffer;
    /** 由 agent 注入的共享 CUDA 流（捕获 kernel 在其上异步提交）。 */
    private Stream cudaStream;
    /** 由 agent 注入的共享完成事件（capture 完成后 record，AI 跨流等待）。 */
    private Event completionEvent;
    /** 由 agent 注入的共享刷新请求（拉模型：AI request，本感受器 consume 后才解码）。 */
    private RefreshRequest feelingRefresh;

    /**
     * 池化的快照纹理（捕获专用资源）：glBlitFramebuffer 的缩放拷贝目标，
     * 隔离 OpenGL 修改源纹理时的竞态。由本类自建自释。
     */
    private Texture snapshotTexture;

    /**
     * 无参构造：解码器不再由工厂装配（工厂不知道 ai 链选了哪个 nn，无法保证配对），
     * 改由 agent 从 ai 链取得、经 {@link #setVisionEncoder} 注入——与 feelingBuffer
     * 同源（nn 家族），配对正确性由结构保证，装配期校验兜底。
     */
    public PossessionSensor() {
    }

    @Override
    public void awaken(VectorBase feelingBuffer, Stream cudaStream, Event completionEvent) {
        // 载体类型（BoolVector/FloatVector）由 ai 链的 nn 家族决定，本感受器不感知——
        // 解码器内部持有载体知识（encode 时向下转型校验，fail-fast）。
        this.feelingBuffer = feelingBuffer;
        this.cudaStream = cudaStream;
        this.completionEvent = completionEvent;
        this.snapshotTexture = new Texture(VisionOps.AI_WIDTH, VisionOps.AI_HEIGHT);
    }

    @Override
    public void setRefreshRequest(RefreshRequest feelingRefresh) {
        this.feelingRefresh = feelingRefresh;
    }

    @Override
    public void setVisionEncoder(VisionEncoder visionEncoder) {
        this.encoder = visionEncoder;
        // 装配期校验（agent 注入序：awaken → setRefreshRequest → setVisionEncoder）：
        // 1) 拉模型握手必须已注入（本感受器是拉模型设计，无请求则永不解码）。
        // 2) 解码器所需载体单位数 == feelingBuffer 尺寸——解码器与缓冲同源于 ai 链
        //    （nn 家族），此处校验是装配期 fail-fast 保险（真善美第3条：尺寸契约实在化）。
        if (visionEncoder == null) {
            throw new IllegalArgumentException("visionEncoder must not be null");
        }
        if (feelingRefresh == null) {
            throw new IllegalStateException("PossessionSensor 未注入感觉刷新请求（拉模型握手缺失），注入序：awaken → setRefreshRequest → setVisionEncoder");
        }
        if (feelingBuffer != null) {
            long required = visionEncoder.requiredUnits(VisionOps.AI_WIDTH, VisionOps.AI_HEIGHT);
            if (required != feelingBuffer.size()) {
                throw new IllegalArgumentException(String.format(
                        "VisionEncoder requires %d carrier units but feelingBuffer has %d (AI retina %dx%d)",
                        required, feelingBuffer.size(), VisionOps.AI_WIDTH, VisionOps.AI_HEIGHT));
            }
        }
    }

    @Override
    public void capture(int textureId, int texWidth, int texHeight) {
        if (encoder == null) {
            throw new IllegalStateException("PossessionSensor 未注入视觉解码器（setVisionEncoder）");
        }
        if (feelingRefresh == null) {
            throw new IllegalStateException("PossessionSensor 未注入感觉刷新请求（setRefreshRequest）");
        }
        // Step 1: 采集（每帧，~0.1ms）——渲染循环间隙内尽快拿走画面（glBlit 深拷贝建立独立副本）。
        VisionOps.snapshot(textureId, texWidth, texHeight, /*->*/ snapshotTexture);
        // Step 2: 按需解码（拉模型：AI 消费过上一份 feeling 才解码，~5ms）。
        // 解码经由快照纹理（GL 纹理）插入 GL 命令序列（渲染流），与渲染串行——
        // 由 onPostRender 回调调用，天然与渲染同流顺序执行，无竞态。
        if (feelingRefresh.consume()) {
            encoder.encode(snapshotTexture, new Span(0, feelingBuffer.size()) {}, cudaStream, /*->*/ feelingBuffer);
            // 视觉写完 feelingBuffer 后在 cudaStream 上 record，供 AI 流跨流等待。
            completionEvent.record(cudaStream.getHandle());
        }
    }

    @Override
    public void shutdown() {
        if (snapshotTexture != null) {
            try { snapshotTexture.close(); } catch (Exception ignored) {}
            snapshotTexture = null;
        }
        if (encoder != null) {
            encoder.close();
        }
        // 共享引用（feelingBuffer/cudaStream/completionEvent/feelingRefresh）由 agent 释放，此处不处理。
        encoder = null;
        feelingBuffer = null;
        cudaStream = null;
        completionEvent = null;
        feelingRefresh = null;
    }
}