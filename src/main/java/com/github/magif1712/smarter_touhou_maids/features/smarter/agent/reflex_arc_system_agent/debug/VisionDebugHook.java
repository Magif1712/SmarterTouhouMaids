package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.debug;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.VectorBase;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamStore;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.core.PossessionManager;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.IAgent;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterClientService;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ReflexArcSystemAgent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * 视觉调试钩子：感受器调试的“开关 + 观察”模式——把 AI 视觉 feeling buffer 周期性 dump 成 PNG。
 * <p>
 * <b>per-maid 开关</b>（真善美第1条“善”：消除 global state）：开关状态不再以单例 volatile 字段留存，
 * 改存 {@link ParamStore}（maid NBT，随存档走，网络同步）。{@link #onClientTick} 自驱每 tick 读
 * ParamStore(maid) 判断。主线程 20Hz，读 ParamStore（HashMap 查询）开销微。
 * <p>
 * <b>零性能损失核心</b>（真善美第3条：把“是否在调试中”这个不实在概念用实在的 ParamStore 读取固化）：
 * 关闭时 {@link #onClientTick} 在 possessing 检查后即 return，后续 copyToHost/像素重建/写盘全不执行。
 * <p>
 * 模式分层（真善美第2条）：上层（与 EffectorDebugHook 同构）开关 + 读 + 短路；
 * 下层（感受器特有）自驱 @SubscribeEvent + copyToHost + 重建图像 + 写盘——因 feeling 在 GPU，
 * 必须自驱从 GPU 取回，不能像 EffectorDebugHook 那样被动接收已在 CPU 的 ActionIntent。
 * <p>
 * <b>载体分派</b>：feeling 载体由 ai 链的 nn 家族决定（BoolVector 位平面 / FloatVector RGB float），
 * 本钩子按载体实例分派重建逻辑——调试观察者如实呈现"ai 实际看到的载体"，与编码器布局约定对齐
 * （位平面：平面优先 bit 装配；RGB float：通道平面式 [0,1] 值）。
 */
@OnlyIn(Dist.CLIENT)
public enum VisionDebugHook {
    INSTANCE;

    private static final Logger LOGGER = LoggerFactory.getLogger("VisionDebug");

    private static final int DUMP_INTERVAL = 100;
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final int PIXELS = WIDTH * HEIGHT;
    private static final int WORDS_PER_PLANE = PIXELS / 32;
    private static final int TOTAL_WORDS = 24 * WORDS_PER_PLANE;
    private static final int TOTAL_FLOATS = 3 * PIXELS;

    private int tickCounter = 0;
    private boolean reportedFirstState = false;

    /** per-maid NBT key（存 ParamStore，随 maid 存档走）。public 供 factory 声明 ParamOption 引用。 */
    public static final String KEY_VISION_DEBUG_ENABLED = "visionDebugEnabled";

    /**
     * 读 per-maid 开关（供 factory 声明 ParamOption 回显 + onClientTick 消费点判断）。
     * maid 为 null 时返回默认 false。
     */
    public static boolean isVisionDebugEnabled(EntityMaid maid) {
        return Boolean.parseBoolean(
                ParamStore.INSTANCE.getString(maid, KEY_VISION_DEBUG_ENABLED, "false"));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!PossessionManager.INSTANCE.isPossessing()) return;
        // per-maid 开关：先取附身 maid（已确认 possessing，必非空），再读 ParamStore 判断。
        // 顺序：phase/possessing 是便宜检查在前，ParamStore 读取在后（未附身时不读 ParamStore）。
        EntityMaid maid = PossessionManager.INSTANCE.getPossessedMaid();
        // 零性能损失核心：关闭时即返回，后续 copyToHost/像素重建/写盘全不执行。
        if (!isVisionDebugEnabled(maid)) return;

        tickCounter++;
        if (tickCounter < DUMP_INTERVAL) return;
        tickCounter = 0;

        // feeling buffer 是反射弧系统代理的内部资源，不进 IAgent 接口。
        // 通过 getAgent() 取当前代理实例，向下转型为 ReflexArcSystemAgent 访问其 debug 专用暴露口。
        // 附属 agent 若有不同的视觉系统，应有自己的 debug hook（本钩子对其 instanceof 检查自然短路）。
        VectorBase feeling = null;
        IAgent agent = SmarterClientService.INSTANCE.getAgent();
        if (agent instanceof ReflexArcSystemAgent) {
            feeling = ((ReflexArcSystemAgent) agent).getFeelingBuffer();
        }
        if (feeling == null || !feeling.isInitialized()) {
            if (!reportedFirstState) {
                LOGGER.warn("[VisionDebug] feeling buffer 尚未初始化，跳过 dump");
                reportedFirstState = true;
            }
            return;
        }

        // 载体分派：按 nn 家族的实际载体选择重建逻辑（与编码器布局约定对齐）。
        // copyToHost 失败时重建方法返回 null，跳过本次 dump。
        BufferedImage img;
        if (feeling instanceof BoolVector boolFeeling) {
            img = rebuildBitplane(boolFeeling);
        } else if (feeling instanceof FloatVector floatFeeling) {
            img = rebuildRgbFloat(floatFeeling);
        } else {
            LOGGER.warn("[VisionDebug] 未知感觉载体类型: {}，跳过 dump", feeling.getClass().getSimpleName());
            return;
        }
        if (img == null) return;

        // 输出到 Minecraft 官方调试目录 debug/ 下的 mod 子目录，跨环境一致：
        //   dev 环境：run/debug/smarter_touhou_maids/vision_debug/
        //   实装环境：.minecraft/debug/smarter_touhou_maids/vision_debug/
        // debug/ 是 Minecraft 原版为调试文件（/debug 命令、profiler 等）设立的官方目录，
        // dump 图片是 AI 视觉调试产物，语义归入 debug 范畴最贴切。
        // 不依赖 getParent()（实装环境会定位到用户主目录），直接基于 GAMEDIR resolve。
        Path dirPath = FMLPaths.GAMEDIR.get()
                .resolve("debug")
                .resolve("smarter_touhou_maids")
                .resolve("vision_debug");
        File dir = dirPath.toFile();
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.error("[VisionDebug] 无法创建输出目录: {}", dir.getAbsolutePath());
            return;
        }
        File file = new File(dir, "dump_" + System.currentTimeMillis() + ".png");
        try {
            ImageIO.write(img, "PNG", file);
            LOGGER.info("[VisionDebug] 视觉dump已保存: {}", file.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("[VisionDebug] 视觉dump保存失败: {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * BoolVector 载体重建（位平面布局，与 BitplaneEncoder 约定对齐）：
     * 24 个位平面（R0-R7, G0-G7, B0-B7）平面优先 bit 装配回 RGB。
     */
    private BufferedImage rebuildBitplane(BoolVector feeling) {
        int[] words = new int[TOTAL_WORDS];
        try {
            feeling.copyToHost(words, TOTAL_WORDS);
        } catch (Exception e) {
            LOGGER.error("[VisionDebug] copyToHost 失败，跳过本次 dump", e);
            return null;
        }

        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                // OpenGL 纹理/framebuffer 原点在左下角（Y 向上），buffer 第 0 行是屏幕底行；
                // BufferedImage 原点在左上角（Y 向下），第 0 行是图像顶部。
                // 翻转 Y 轴：buffer 最后一行（屏幕顶部，蓝天）映射到图像第 0 行。
                int pixelIndex = (HEIGHT - 1 - y) * WIDTH + x;
                int wordIdx = pixelIndex / 32;
                int bitPos = pixelIndex % 32;

                int r = 0, g = 0, b = 0;
                int rBase = 0 * WORDS_PER_PLANE;
                int gBase = 8 * WORDS_PER_PLANE;
                int bBase = 16 * WORDS_PER_PLANE;
                for (int bp = 0; bp < 8; bp++) {
                    r |= ((words[rBase + wordIdx] >>> bitPos) & 1) << bp;
                    g |= ((words[gBase + wordIdx] >>> bitPos) & 1) << bp;
                    b |= ((words[bBase + wordIdx] >>> bitPos) & 1) << bp;
                    rBase += WORDS_PER_PLANE;
                    gBase += WORDS_PER_PLANE;
                    bBase += WORDS_PER_PLANE;
                }
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    /**
     * FloatVector 载体重建（RGB float 布局，与 RgbFloatEncoder 约定对齐）：
     * 通道平面式（R 平面 | G 平面 | B 平面，各 w*h 元素），值域 [0,1]（v/255 归一化）。
     */
    private BufferedImage rebuildRgbFloat(FloatVector feeling) {
        float[] floats = new float[TOTAL_FLOATS];
        try {
            feeling.copyToHost(floats, TOTAL_FLOATS);
        } catch (Exception e) {
            LOGGER.error("[VisionDebug] copyToHost 失败，跳过本次 dump", e);
            return null;
        }

        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                // Y 翻转同位平面版：buffer 行序自下而上，图像行序自上而下。
                int pixelIndex = (HEIGHT - 1 - y) * WIDTH + x;
                int r = clampToByte(floats[0 * PIXELS + pixelIndex]);
                int g = clampToByte(floats[1 * PIXELS + pixelIndex]);
                int b = clampToByte(floats[2 * PIXELS + pixelIndex]);
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    /** [0,1] 浮点 → [0,255] 字节（钳制越界值，防御解码异常数据）。 */
    private static int clampToByte(float v) {
        int i = Math.round(v * 255f);
        return Math.max(0, Math.min(255, i));
    }
}
