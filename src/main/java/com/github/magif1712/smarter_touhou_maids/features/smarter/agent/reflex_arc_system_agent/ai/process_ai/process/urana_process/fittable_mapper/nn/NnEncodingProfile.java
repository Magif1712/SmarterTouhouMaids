package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn;

/**
 * NN 编码剖面：把"某 nn 家族对 feeling/behavior/dt/时间方位的编码长度"这个不实在约束，
 * 实在化为一个纯数据对象（真善美第4条）。
 * <p>
 * 由各 nn 工厂的 {@code encodingProfile()} 提供，UranaProcessFactory 据此创建
 * InputVectorDomain/OutputVectorDomain，注入 UranaSystem。换 nn 实现时 profile 跟着换，
 * urana 域布局零改动（真善美第3条）。
 */
public final class NnEncodingProfile {
    private final int feelingLength;
    private final int behaviorLength;
    private final int dtLength;
    private final int timeOrientationUnitLength;

    public NnEncodingProfile(int feelingLength, int behaviorLength, int dtLength, int timeOrientationUnitLength) {
        this.feelingLength = feelingLength;
        this.behaviorLength = behaviorLength;
        this.dtLength = dtLength;
        this.timeOrientationUnitLength = timeOrientationUnitLength;
    }

    public int getFeelingLength() {
        return feelingLength;
    }

    public int getBehaviorLength() {
        return behaviorLength;
    }

    public int getDtLength() {
        return dtLength;
    }

    public int getTimeOrientationUnitLength() {
        return timeOrientationUnitLength;
    }
}
