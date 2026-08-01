package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.debug;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.core.PossessionManager;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.IAgent;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterClientService;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ReflexArcSystemAgent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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

    private int tickCounter = 0;
    private boolean reportedFirstState = false;

    /**
     * 调试开关（实在的开关状态，承载不实在的“是否在调试中”概念）。
     * 默认关闭：游戏启动时不产生任何 dump 开销。
     * volatile：由 AutoTaskConfigScreen 的 UI 线程写入，onClientTick 读取，保证可见性。
     */
    private volatile boolean enabled = false;

    /**
     * 设置调试开关状态。由 AutoTaskConfigScreen 的“AI视觉调试”按钮（模式1：UI 触发）调用。
     * 开启后下一 tick 起按 DUMP_INTERVAL 开始 dump；
     * 关闭后立即在 onClientTick 入口短路，调试逻辑零执行（零性能损失）。
     */
    public void setEnabled(boolean value) {
        if (this.enabled == value) return;
        this.enabled = value;
        LOGGER.info("[VisionDebug] 调试已{}", value ? "开启" : "关闭");
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable(
                    value ? "msg.smarter_touhou_maids.vision_debug.on"
                            : "msg.smarter_touhou_maids.vision_debug.off"));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // 零性能损失核心：关闭时第一行即返回，后续 copyToHost/像素重建/写盘全不执行。
        if (!enabled) return;
        if (event.phase != TickEvent.Phase.END) return;
        if (!PossessionManager.INSTANCE.isPossessing()) return;

        tickCounter++;
        if (tickCounter < DUMP_INTERVAL) return;
        tickCounter = 0;

        // feeling buffer 是反射弧系统代理的内部资源，不进 IAgent 接口。
        // 通过 getAgent() 取当前代理实例，向下转型为 ReflexArcSystemAgent 访问其 debug 专用暴露口。
        // 附属 agent 若有不同的视觉系统，应有自己的 debug hook（本钩子对其 instanceof 检查自然短路）。
        BoolVector feeling = null;
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

        int[] words = new int[TOTAL_WORDS];
        try {
            feeling.copyToHost(words, TOTAL_WORDS);
        } catch (Exception e) {
            LOGGER.error("[VisionDebug] copyToHost 失败，跳过本次 dump", e);
            return;
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
}
