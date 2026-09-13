package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor;

/**
 * 拉模型感受器提供者（契约标记接口，推论2 的类型令牌）。
 * <p>
 * 语义：采集与解码分离——每帧快照保鲜，仅当 AI 经 RefreshRequest 请求时才解码写入
 * feelingBuffer（新版 on_demand_possession_sensor 的模型）。需要消费方流程具备
 * 刷新驱动能力（每轮 request），否则感受器饿死。
 * 消费方（如新版 urana 流程）经 {@code Branch.addRequirement(AgentNodeKeys.SENSOR, IPullEncodedSensor.class)}
 * 声明只兼容本契约的感受器；GUI 经 ConstraintSolver 过滤掉不满足的选项——无硬编码 if/switch。
 * <p>
 * 标记在<b>工厂</b>上而非实例上：SENSOR 插槽的分支产物是工厂提供者
 * （感受器实例化需要 ai.feelingSize() 等跨兄弟参数，由消费方父工厂带参实例化）。
 */
public interface IPullEncodedSensor extends SensorFactory {
    /** 契约类型令牌：本接口即令牌（freeze 类型校验与 ConstraintSolver 判定用）。 */
    @Override
    default Class<? extends SensorFactory> producedType() {
        return IPullEncodedSensor.class;
    }
}
