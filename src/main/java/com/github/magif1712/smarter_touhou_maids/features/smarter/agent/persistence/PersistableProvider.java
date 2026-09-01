package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.persistence;

/**
 * Factory 的可选能力契约（第三 Provider 管道）：声明该层模式的实例是否产生可持久化数据。
 * <p>
 * 与 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param.ParamPanelProvider}
 * （参数管道）和 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.debug.DebugPanelProvider}
 * （调试管道）完全对称——三管道同构，各自声明一种 factory 能力，由 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterLayerWalker}
 * 遍历收集。
 * <p>
 * <b>声明轨与执行轨正交</b>（真善美第2条）：
 * <ul>
 *   <li><b>声明轨</b>（本接口，factory 静态）：告诉 GUI"这条路径默认是否持久化"——
 *       路径默认开关 = 路径上任一 factory 声明 {@code true}（见
 *       {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterLayerWalker#anyPersistable}）。</li>
 *   <li><b>执行轨</b>（实例运行时，各层 save 方法）：告诉磁盘"实际要存什么数据"——
 *       声明 true 的层在 save 时调自己的 save 方法写文件。</li>
 * </ul>
 * 两轨正交：声明轨决定 GUI 默认开关，执行轨决定磁盘数据。各自简单，互不耦合。
 * <p>
 * <b>随各层模式动态切换</b>（真善美第3条）：选 nn=standard_bnn → StandardBnnNnFactory 声明 true；
 * 换 nn=纯规则（无权重） → 那个 nn factory 不实现本接口（等同 false）。GUI 默认开关跟随路径自动变化，
 * 不硬编码（加新 nn 不改默认开关逻辑）。
 * <p>
 * <b>不实现本接口的 factory 自动等同 false</b>（与 ParamPanelProvider/DebugPanelProvider 同机制）。
 */
public interface PersistableProvider {
    /**
     * 该 factory 的实例是否产生可持久化数据。
     * <p>
     * 路径默认持久化开关 = 路径上任一 factory 实现本接口且返回 true。
     *
     * @return true = 该层有可持久化数据（NN 权重/梯度/继承/时间等）；false = 无
     */
    boolean hasPersistableData();
}
