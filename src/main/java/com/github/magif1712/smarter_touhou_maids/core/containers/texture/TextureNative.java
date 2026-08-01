package com.github.magif1712.smarter_touhou_maids.core.containers.texture;

import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

/**
 * JNI 桥接类的 Java 声明，用于 Texture（纹理）的原生操作。
 * <p>
 * 设计模式：对标 {@link com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorNative}，
 * 提供纹理资源的创建、属性访问、快照、销毁等底层 GPU 操作接口。
 * <p>
 * 接口风格（与 VectorNative 一致）：
 * <ul>
 *   <li>Create/Delete：管理对象生命周期</li>
 *   <li>Getters：读取对象状态</li>
 *   <li>行为方法：执行对象操作</li>
 * </ul>
 */
public class TextureNative {
    static {
        NativeLibLoader.ensureLoaded();
    }

    /**
     * 创建 Texture 对象并返回原生句柄。
     *
     * @param width  纹理宽度（像素）
     * @param height 纹理高度（像素）
     * @return 原生 Texture 对象的指针句柄（失败返回 0）
     */
    public static native long _createAndRegister(int width, int height);

    /**
     * 销毁 Texture 对象及其关联的 GPU 资源。
     *
     * @param handle 原生 Texture 对象的指针句柄
     */
    public static native void _destroy(long handle);

    /**
     * 获取 OpenGL 纹理 ID。
     *
     * @param handle 原生 Texture 对象的指针句柄
     * @return OpenGL 纹理 ID
     */
    public static native int _getTextureId(long handle);

    /**
     * 获取 CUDA 资源句柄。
     *
     * @param handle 原生 Texture 对象的指针句柄
     * @return CUDA 资源的 opaque 指针值
     */
    public static native long _getCudaResourceHandle(long handle);

    /**
     * 获取纹理宽度。
     *
     * @param handle 原生 Texture 对象的指针句柄
     * @return 纹理宽度（像素）
     */
    public static native int _getWidth(long handle);

    /**
     * 获取纹理高度。
     *
     * @param handle 原生 Texture 对象的指针句柄
     * @return 纹理高度（像素）
     */
    public static native int _getHeight(long handle);

    /**
     * 将源纹理内容快照到此纹理（GPU 侧异步拷贝）。
     * <p>
     * 实际拷贝区域 = min(srcWidth, 纹理宽度) × min(srcHeight, 纹理高度)，
     * 采样分辨率由 Java 侧决定，C 侧不硬编码任何分辨率。
     *
     * @param handle       原生 Texture 对象的指针句柄
     * @param srcTextureId 源纹理的 OpenGL ID
     * @param srcWidth     源纹理实际宽度（像素，由 Java 侧传入）
     * @param srcHeight    源纹理实际高度（像素，由 Java 侧传入）
     */
    public static native void _snapshotFrom(long handle, int srcTextureId, int srcWidth, int srcHeight);
}