package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.sensor.possession_sensor.vision;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.texture.Texture;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.execution.stream.Stream;

public class VisionOps {

    /** AI 视网膜固定分辨率 —— 与游戏窗口大小无关 */
    public static final int AI_WIDTH = 1920;
    public static final int AI_HEIGHT = 1080;

    /**
     * 通过快照机制捕获屏幕到 BoolVector。
     *
     * <h3>执行模型</h3>
     * <ul>
     *   <li><b>CPU 侧异步</b>：调用后立即返回，不等待 CUDA kernel 完成。</li>
     *   <li><b>GPU 侧顺序执行</b>：
     *     <ol>
     *     <li>OpenGL 队列：glBlitFramebuffer 将源纹理缩放拷贝到临时纹理 (~0.1ms)</li>
     *       <li>CUDA 队列：kernel 从临时纹理编码为 Bitplane 并写入 BoolVector (~5ms)</li>
     *     </ol>
     *     两个队列各自内部有序，互不干扰（通过临时纹理隔离）。
     *   </li>
     *   <li><b>中间纹理隔离</b>：使用独立的 Texture 对象作为快照目标，
     *       避免 OpenGL 修改源纹理时的竞态条件。</li>
     * </ul>
     *
     * <h3>适用场景</h3>
     * <ul>
     *   <li>在非渲染线程调用（如 AI Tick 线程），避免阻塞渲染管线</li>
     *   <li>调用后有足够的 GPU 空闲时间让 kernel 完成（如 200ms AI 周期）</li>
     *   <li>或在读取数据前显式调用 {@code stream.synchronize()}</li>
     * </ul>
     *
     * <h3>线程安全说明</h3>
     * 必须在拥有 OpenGL 上下文的线程中调用（通常是主线程/渲染线程），
     * 因为内部会执行 glCopyImageSubData 操作。
     *
     * @param textureId    要捕获的 OpenGL 纹理 ID（源纹理，应为当前帧画面）
     * @param tempTexture  预分配的临时纹理（由调用方池化管理，避免反复创建销毁）
     * @param dst          目标 BoolVector（存储编码后的位平面数据）
     * @param destSpan     定义 dst 中的写入区间（长度 >= AI_WIDTH * AI_HEIGHT * 24）
     * @param texWidth     源纹理实际宽度（像素）
     * @param texHeight    源纹理实际高度（像素）
     * @param stream       CUDA 流（用于提交 kernel 和管理异步执行）
     *
     * @throws IllegalArgumentException 参数无效
     * @throws IllegalStateException dst 或 tempTexture 未初始化
     */
    public static void captureScreenViaSnapshot(
            int textureId,
            Texture tempTexture,
            BoolVector dst,
            Span destSpan,
            int texWidth,
            int texHeight,
            Stream stream) {
        if (textureId <= 0) {
            throw new IllegalArgumentException("Invalid texture ID: " + textureId);
        }
        if (!dst.isInitialized()) {
            throw new IllegalStateException("Destination BoolVector has no allocated device memory.");
        }
        if (tempTexture == null || !tempTexture.isInitialized()) {
            throw new IllegalStateException("Temp texture is null or not initialized.");
        }
        if (texWidth <= 0 || texHeight <= 0) {
            throw new IllegalArgumentException(
                    "Invalid texture dimensions: " + texWidth + "x" + texHeight);
        }

        long requiredBits = (long) AI_WIDTH * AI_HEIGHT * 24L;
        if (destSpan.getLength() < requiredBits) {
            throw new IllegalArgumentException(String.format(
                    "Destination span length (%d) must be >= AI retina area * 24 (%d).",
                    destSpan.getLength(), requiredBits));
        }
        if (destSpan.getOffset() + destSpan.getLength() > dst.size()) {
            throw new IllegalArgumentException("Destination span is out of bounds for the BoolVector.");
        }

        VisionNative._captureScreenViaSnapshot(
                textureId,
                tempTexture.getHandle(),
                tempTexture.getTextureId(),
                tempTexture.getCudaResourceHandle(),
                dst.handle(),
                destSpan.getOffset(),
                texWidth,
                texHeight,
                AI_WIDTH,
                AI_HEIGHT,
                stream.getHandle()
        );
    }

    public static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(VisionNative::_cleanup));
    }
}