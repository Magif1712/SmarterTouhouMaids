package com.github.magif1712.smarter_touhou_maids.core.containers.texture;

/**
 * GPU 纹理资源容器，管理 OpenGL 纹理及其 CUDA 互操作注册。
 * <p>
 * 设计原则（真善美）：
 * <ul>
 *   <li><b>真</b>：用实在的 ID 和尺寸表示不实在的 GPU 资源对象，
 *       IDE 可直接检查 textureId、cudaResourceHandle 等状态字段</li>
 *   <li><b>善</b>：遵循 AutoCloseable 资源管理模式，
 *       与 core.containers.vector 包中的 BoolVector 等容器风格一致</li>
 *   <li><b>美</b>：名称反映本质属性（"纹理"）而非使用场景（"临时纹理"），
 *       可扩展为渲染目标、数据缓冲等多种用途</li>
 * </ul>
 * <p>
 * 典型用途：
 * <ul>
 *   <li>视觉采样中的快照隔离层（避免 CUDA-OpenGL 竞态）</li>
 *   <li>GPU 计算的中间渲染目标</li>
 *   <li>CUDA kernel 的只读纹理输入</li>
 * </ul>
 */
public class Texture implements AutoCloseable {

    private final long handle;
    private final int textureId;
    private final long cudaResourceHandle;
    private final int width;
    private final int height;
    private boolean initialized = false;

    /**
     * 创建指定尺寸的 RGBA8 格式纹理并注册到 CUDA。
     *
     * @param width  纹理宽度（像素），必须 > 0
     * @param height 纹理高度（像素），必须 > 0
     * @throws IllegalArgumentException 如果尺寸参数无效
     */
    public Texture(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid texture dimensions: " + width + "x" + height);
        }

        this.handle = TextureNative._createAndRegister(width, height);
        
        if (this.handle == 0) {
            throw new RuntimeException("Failed to create native Texture object");
        }

        this.textureId = TextureNative._getTextureId(this.handle);
        this.cudaResourceHandle = TextureNative._getCudaResourceHandle(this.handle);
        this.width = TextureNative._getWidth(this.handle);
        this.height = TextureNative._getHeight(this.handle);
        this.initialized = true;
    }

    /**
     * 将源纹理的内容快照到此纹理（GPU 侧深拷贝）。
     * <p>
     * 实现原理：调用 glBlitFramebuffer 在 OpenGL 命令队列中插入异步的缩放拷贝操作，
     * 开销约 0.1ms，不会造成 CPU-GPU 数据传输。
     * <p>
     * 实际拷贝区域 = 整源 (srcWidth × srcHeight) 缩放 blit 填满本纹理全域，GL_NEAREST 跨步抽样：
     * <ul>
     *   <li>源 > 本纹理 (2K→1080p)：blit 降采样（跨步），整窗口可见</li>
     *   <li>源 = 本纹理 (1080p)：1:1</li>
     *   <li>源 < 本纹理 (720p→1080p)：blit 升采样（像素复制），整窗口可见</li>
     * </ul>
     * 采样分辨率由 Java 侧决定（传入源实际尺寸），C 侧不硬编码任何分辨率。
     * <p>
     * <b>调用约束</b>：必须在 OpenGL 上下文线程（通常为渲染线程）中调用。
     *
     * @param srcTextureId 源纹理的 OpenGL 纹理 ID
     * @param srcWidth     源纹理实际宽度（像素）
     * @param srcHeight    源纹理实际高度（像素）
     * @throws IllegalStateException 如果此纹理已关闭或未初始化
     * @throws IllegalArgumentException 如果源纹理 ID 无效
     */
    public void snapshotFrom(int srcTextureId, int srcWidth, int srcHeight) {
        if (!initialized) {
            throw new IllegalStateException("Texture has been closed or not initialized");
        }
        if (srcTextureId <= 0) {
            throw new IllegalArgumentException("Invalid source texture ID: " + srcTextureId);
        }
        if (srcWidth <= 0 || srcHeight <= 0) {
            throw new IllegalArgumentException(
                    "Invalid source dimensions: " + srcWidth + "x" + srcHeight);
        }

        TextureNative._snapshotFrom(handle, srcTextureId, srcWidth, srcHeight);
    }

    public long getHandle() {
        return handle;
    }

    public int getTextureId() {
        return textureId;
    }

    public long getCudaResourceHandle() {
        return cudaResourceHandle;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void close() {
        if (initialized) {
            TextureNative._destroy(handle);
            initialized = false;
        }
    }
}