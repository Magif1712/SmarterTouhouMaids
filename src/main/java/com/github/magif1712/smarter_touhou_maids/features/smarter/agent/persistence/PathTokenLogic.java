package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence;

import java.util.function.Function;

/**
 * 路径 token 拼接的<b>纯函数核心</b>（真善美第4条：把"沿递归链拼 token"这个不实在的算法流程，
 * 实在化为一个不依赖 Minecraft 类的纯函数，可用 golden 样本锁死行为）。
 * <p>
 * 语义与重构前 {@code SmarterLayerWalker.pathTokenChain} 完全一致：
 * <ol>
 *   <li>从 rootLayerId 起，逐层取"当前选中 entry"（无选中回退该层默认 entry）。</li>
 *   <li>每层贡献一个 path 分量，分量间用 {@code "__"} 拼接。</li>
 *   <li>entry 声明了下层 id 则下钻，否则终止（叶子）。</li>
 *   <li>任一层未注册 / entry 未注册 / 无选中且无默认：该层及更下层不贡献分量（终止）。</li>
 * </ol>
 * 本类不 import 任何 Minecraft / Forge 类，可在纯 JUnit 环境运行。
 */
public final class PathTokenLogic {
    private PathTokenLogic() {
    }

    /**
     * 层视图（纯数据接口）。生产环境由 Registry 适配；测试用手工图。
     * 所有方法返回 null 表示"不存在"（未注册 / 无默认 / 叶子）。
     */
    public interface Layer {
        /** 该层默认 entry id（字符串），无默认返回 null。 */
        String defaultEntryId();

        /** 指定 entry 的 id path 分量（如 "smarter"）；entry 未注册返回 null。 */
        String entryPath(String entryId);

        /** 指定 entry 声明的下层 id（字符串）；叶子返回 null。 */
        String subLayerId(String entryId);
    }

    /**
     * 拼路径 token。
     *
     * @param rootLayerId    根层 id（字符串形式）
     * @param layerById      按层 id 查层视图；未注册返回 null
     * @param currentByLayer 按层 id 查当前选中 entry id；未设置返回 null（回退默认）
     * @return 拼接后的 token（无任何可达层时返回空串）
     */
    public static String pathToken(String rootLayerId,
                                   Function<String, Layer> layerById,
                                   Function<String, String> currentByLayer) {
        StringBuilder sb = new StringBuilder();
        appendChain(rootLayerId, layerById, currentByLayer, sb);
        return sb.toString();
    }

    private static void appendChain(String layerId,
                                    Function<String, Layer> layerById,
                                    Function<String, String> currentByLayer,
                                    StringBuilder sb) {
        Layer layer = layerById.apply(layerId);
        if (layer == null) {
            return;
        }
        String currentId = currentByLayer.apply(layerId);
        if (currentId == null) {
            currentId = layer.defaultEntryId();
        }
        if (currentId == null) {
            return;
        }
        String path = layer.entryPath(currentId);
        if (path == null) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("__");
        }
        sb.append(path);
        String subLayerId = layer.subLayerId(currentId);
        if (subLayerId != null) {
            appendChain(subLayerId, layerById, currentByLayer, sb);
        }
    }
}
