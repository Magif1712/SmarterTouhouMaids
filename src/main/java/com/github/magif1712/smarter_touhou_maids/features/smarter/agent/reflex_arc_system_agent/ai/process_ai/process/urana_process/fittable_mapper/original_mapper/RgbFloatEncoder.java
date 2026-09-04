package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.original_mapper;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.texture.Texture;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.core.execution.stream.Stream;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.VisionEncoder;

/**
 * RGB float 解码器（CNN 载体用）：把快照纹理（GL 纹理）的 RGBA 像素解码为
 * 归一化 [0,1] 的 RGB float 写入 FloatVector——通道平面式布局
 * （R 平面 | G 平面 | B 平面，各 w*h 元素），与位平面解码器的平面优先约定一致。
 * RGB float 是 CNN（浮点激活网络）可利用的数据类型，
 * 故住 original_mapper（fittable_mapper 层：Texture 解码输出为特定 NN 可利用的数据类型）。
 * <p>
 * CNN 是稀疏随机连接网络（push/pull kernel 按随机投影索引连接），输入为平坦 float 数组，
 * 对布局不敏感——布局只需全族解码器约定一致即可。
 */
public class RgbFloatEncoder implements VisionEncoder {

    @Override
    public void encode(Texture snapshotTexture, Span destSpan, Stream stream, /*->*/ VectorBase output) {
        if (!(output instanceof FloatVector dst)) {
            throw new IllegalArgumentException("RgbFloatEncoder requires FloatVector output");
        }
        if (snapshotTexture == null || !snapshotTexture.isInitialized()) {
            throw new IllegalStateException("Snapshot texture is null or not initialized.");
        }
        if (!dst.isInitialized()) {
            throw new IllegalStateException("Destination FloatVector has no allocated device memory.");
        }

        // 尺寸契约：解码区域 = 快照纹理全域（纹理自身携带尺寸，不依赖 sensor 域常量）。
        long requiredUnits = requiredUnits(snapshotTexture.getWidth(), snapshotTexture.getHeight());
        if (destSpan.getLength() < requiredUnits) {
            throw new IllegalArgumentException(String.format(
                    "Destination span length (%d) must be >= w*h*3 elements (%d).",
                    destSpan.getLength(), requiredUnits));
        }
        if (destSpan.getOffset() + destSpan.getLength() > dst.size()) {
            throw new IllegalArgumentException("Destination span is out of bounds for the FloatVector.");
        }

        RgbFloatEncoderNative._encode(
                snapshotTexture.getHandle(),
                destSpan.getOffset(),
                stream.getHandle(),
                /*->*/ dst.handle());
    }

    @Override
    public long requiredUnits(int aiWidth, int aiHeight) {
        return (long) aiWidth * aiHeight * 3L;   // R/G/B 各一个值平面（单位：元素）
    }

    @Override
    public void close() {
        // native 侧无持久资源（Map/Unmap 每次解码内完成，texObj 即建即毁）
    }
}
