package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.vision;

import com.github.magif1712.smarter_touhou_maids.core.containers.texture.Texture;

public class VisionOps {

    /** AI 视网膜固定分辨率 —— 与游戏窗口大小无关 */
    public static final int AI_WIDTH = 1920;
    public static final int AI_HEIGHT = 1080;

    /**
     * 将 Minecraft 当前帧深拷贝到快照纹理（采集原语）。
     * <p>
     * glBlitFramebuffer：GPU→GPU 像素拷贝 + 缩放，~0.1ms。深拷贝建立独立副本，
     * 后续 Minecraft 对源纹理的任何操作（覆盖/resize/格式变更）都不影响我们。
     * <p>
     * 采集是稳定原语：永远只有 glBlit 深拷贝这一种方式；解码是可插拔模式
     * （住 fittable_mapper 层，如 original_mapper 的位平面解码器）。
     * 每帧调用（保鲜）：解码时刻快照纹理一定是最新完整帧。
     *
     * <h3>DPS</h3>
     * 入参：源纹理ID + 源纹理尺寸<br>
     * 出参：snapshotTexture（深拷贝后的独立副本）<br>
     * 数据流：srcTextureId → glBlit → snapshotTexture[GPU显存]
     *
     * @param textureId       要捕获的 OpenGL 纹理 ID（源纹理，应为当前帧画面）
     * @param texWidth        源纹理实际宽度（像素）
     * @param texHeight       源纹理实际高度（像素）
     * @param snapshotTexture 预分配的快照纹理（深拷贝目标，调用方池化管理）
     */
    public static void snapshot(
            int textureId, int texWidth, int texHeight,
            /*->*/ Texture snapshotTexture) {
        if (textureId <= 0) {
            throw new IllegalArgumentException("Invalid texture ID: " + textureId);
        }
        if (snapshotTexture == null || !snapshotTexture.isInitialized()) {
            throw new IllegalStateException("Snapshot texture is null or not initialized.");
        }
        if (texWidth <= 0 || texHeight <= 0) {
            throw new IllegalArgumentException(
                    "Invalid texture dimensions: " + texWidth + "x" + texHeight);
        }
        snapshotTexture.snapshotFrom(textureId, texWidth, texHeight);
    }
}
