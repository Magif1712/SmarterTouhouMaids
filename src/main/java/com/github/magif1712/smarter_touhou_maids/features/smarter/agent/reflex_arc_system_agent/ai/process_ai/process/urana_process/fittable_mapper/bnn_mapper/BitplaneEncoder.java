package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.bnn_mapper;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.texture.Texture;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.core.execution.stream.Stream;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.VisionEncoder;

/**
 * 位平面解码器（BNN 载体用）：把快照纹理（GL 纹理）的 RGBA 像素拆成 24 个位平面
 * （R0-R7, G0-G7, B0-B7）写入 BoolVector——位平面是 BNN 可利用的数据类型，
 * 故住 bnn_mapper（fittable_mapper 层：Texture 解码输出为特定 NN 可利用的数据类型）。
 * <p>
 * 解码逻辑（Map→GetArray→CreateTexObj→kernel→Destroy→Unmap）从旧视觉采集路径
 * 的 native 侧原样搬出，逻辑不变。
 */
public class BitplaneEncoder implements VisionEncoder {

    @Override
    public void encode(Texture snapshotTexture, Span destSpan, Stream stream, /*->*/ VectorBase output) {
        if (!(output instanceof BoolVector dst)) {
            throw new IllegalArgumentException("BitplaneEncoder requires BoolVector output");
        }
        if (snapshotTexture == null || !snapshotTexture.isInitialized()) {
            throw new IllegalStateException("Snapshot texture is null or not initialized.");
        }
        if (!dst.isInitialized()) {
            throw new IllegalStateException("Destination BoolVector has no allocated device memory.");
        }

        // 尺寸契约：解码区域 = 快照纹理全域（纹理自身携带尺寸，不依赖 sensor 域常量）。
        long requiredUnits = requiredUnits(snapshotTexture.getWidth(), snapshotTexture.getHeight());
        if (destSpan.getLength() < requiredUnits) {
            throw new IllegalArgumentException(String.format(
                    "Destination span length (%d) must be >= w*h*24 bits (%d).",
                    destSpan.getLength(), requiredUnits));
        }
        if (destSpan.getOffset() + destSpan.getLength() > dst.size()) {
            throw new IllegalArgumentException("Destination span is out of bounds for the BoolVector.");
        }

        BitplaneEncoderNative._encode(
                snapshotTexture.getHandle(),
                destSpan.getOffset(),
                stream.getHandle(),
                /*->*/ dst.handle());
    }

    @Override
    public long requiredUnits(int aiWidth, int aiHeight) {
        return (long) aiWidth * aiHeight * 24L;   // R/G/B 各 8 bit 平面（单位：bit）
    }

    @Override
    public void close() {
        // native 侧无持久资源（Map/Unmap 每次解码内完成，texObj 即建即毁）
    }
}