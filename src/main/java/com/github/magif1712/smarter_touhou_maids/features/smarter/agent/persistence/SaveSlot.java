package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence;

/**
 * 持久化槽位：把"maid + 根叶路径 + 版本"这个不实在的三维定位，实在化为一个对象（真善美第4条）。
 * <p>
 * 由 {@link SaveSlotFactory} 创建，透传给各层 factory/实例。各层用自己的 layerId 问自己的持久化目录，
 * 上层不感知下层格式，下层不感知上层路径（真善美第2/3条：C 中"每层自管持久化"是模式，D 中 SaveSlot
 * 只透传通用 layerPath，不写死任何具体层名）。
 * <p>
 * <b>通用 layerPath</b>（真善美第3条）：本接口只有 {@link #layerPath(String)}，不写死
 * {@code nnPath()}/{@code uranaPath()}——加新层（如未来新 process 或新 nn 子层）时不改本接口，
 * 新层用自己的 layerId 问路径即可。这与 ParamPanelProvider/DebugPanelProvider 的"加新 factory
 * 不改管道"同构。
 * <p>
 * <b>时机对称</b>：load 时（create 组装）与 save 时（shutdown 前）都用同一个槽位抽象，但指向不同版本
 * 目录——load 读最新已有版本，save 写新时间戳版本（{@link SaveSlotFactory#newVersion}）。
 */
public interface SaveSlot {
    /**
     * 该 maid+路径+版本的根目录绝对路径。
     * <p>
     * 形如 {@code <GAMEDIR>/smarter_touhou_maids/persistence/<maidUUID>/<path-token>/v<timestamp>/}。
     *
     * @return 版本根目录绝对路径
     */
    String rootPath();

    /**
     * 各层用自己的 layerId 问持久化目录（如 "nn"/"urana"/未来新层）。
     * <p>
     * 返回 {@code rootPath()/layerId} 的绝对路径。调用方负责 {@code new File(path).mkdirs()} 创建子目录。
     * 加新层不改本接口（真善美第3条）。
     *
     * @param layerId 层标识（由各层自备，如 "nn"、"urana"）
     * @return 该层的持久化目录绝对路径
     */
    String layerPath(String layerId);
}
