package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

/**
 * 分支工厂：由 Branch 持有，负责产出该分支的实例。
 * <p>
 * 设计原则5（DPS）：统一无返回值，出参经 {@code /*->*&#47;} 右侧的 {@link OutSlot} 注入。
 * <p>
 * <b>工厂自驱组装</b>：工厂只从 {@link AssemblyContext} 取自己 Branch 声明过的具名子实例，
 * 外层（Resolver / 更上层工厂）不感知本层的组装细节。未声明的子插槽根本不会被解析。
 * <p>
 * <b>双轨兼容</b>：旧式工厂（签名 {@code create(CompoundTag, EntityMaid, SaveSlot)}）经
 * legacy 适配器实现本接口，内部仍自 resolve 下层——P2 双轨期行为不变。
 *
 * @param <T> 产出实例类型
 */
public interface Factory<T> {
    /**
     * 产出类型令牌：freeze 时校验 {@code producedType} 可被所在 Node 的 {@code NodeKey.type} 接受。
     * 允许返回子类型（通配上界）——契约标记接口（如 IPushEncodedSensor）即子类型令牌。
     */
    Class<? extends T> producedType();

    /**
     * 组装实例。
     *
     * @param ctx 组装上下文（maid / saveSlot / 具名子实例解析）
     * @param out 出参缓冲区（必然注入）
     */
    void create(AssemblyContext ctx, /*->*/ OutSlot<T> out);
}
