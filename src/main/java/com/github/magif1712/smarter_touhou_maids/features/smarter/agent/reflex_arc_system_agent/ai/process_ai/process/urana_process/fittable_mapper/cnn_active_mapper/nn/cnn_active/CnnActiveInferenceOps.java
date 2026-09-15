package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper.nn.cnn_active;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.cnn.containers.CnnFwTraceForBw;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.cnn.containers.CnnHyperparameters;

/**
 * CNN-Active 推理桥接层（照抄 {@code CnnInferenceOps} + 两个新入参）：接收上层对象，提取句柄，
 * 调用原生方法。
 * <p>
 * {@code y} 统一为"kernel 写入的输出缓冲区"（DPS 契约：forward 后 y 即为结果）。
 * native 侧 {@code traceY} 即此 {@code y}，{@code traceZ} 为 0 走 NoTrace，非 0 走 StoreTrace。
 * <p>
 * 新入参（相对原 CNN）：
 * <ul>
 *   <li>{@code prevB}——NN 自持的上一拍行为段（D4 的时间邻域源），256 float。</li>
 *   <li>{@code activationId}——D2 的显式入参（1=tanh / 2=clipped-linear）。</li>
 * </ul>
 * <p>
 * {@code cnnActiveRefreshCache} 由 {@code p} 重算 {@code idx0/idx1/w0/w1}（非热路径，stream 0 + 同步）。
 * 复用 carrier（{@link CnnHyperparameters}）的 idx/w 槽位，但派生逻辑由本模块自己的 kernel 生产——
 * idx/w 是 p 的派生缓存，其"生产者"必须与"消费者"（本模块的 pull kernel）同源，否则原 CNN 一旦
 * 演化插值约定，本模块的被冻结消费者会静默失配（定理1(2)：不与原 CNN 共享可变演化路径）。
 */
public class CnnActiveInferenceOps {

    public static void cnnActiveForwardLayer(FloatVector x, CnnHyperparameters hp, FloatVector prevB, int activationId, long stream /* -> */, FloatVector y, CnnFwTraceForBw trace) {
        long traceZ = (trace != null) ? trace.z.requireHandle() : 0L;
        CnnActiveInferenceOpsNative._cnnActiveForwardLayer(x.requireHandle(), hp.getP().requireHandle(), hp.getQ().requireHandle(), hp.getL().requireHandle(), hp.getR().requireHandle(), hp.getB().requireHandle(), hp.getIdx0().requireHandle(), hp.getIdx1().requireHandle(), hp.getW0().requireHandle(), hp.getW1().requireHandle(), prevB.requireHandle(), activationId, hp.getSizeA0(), hp.getSizeA1(), stream /* -> */, traceZ, y.requireHandle());
    }

    public static void cnnActiveRefreshCache(CnnHyperparameters hp, long stream /* -> */) {
        CnnActiveInferenceOpsNative._cnnActiveRefreshCache(hp.getP().requireHandle(), hp.getSizeA0(), hp.getSizeA1(), stream /* -> */, hp.getIdx0().requireHandle(), hp.getIdx1().requireHandle(), hp.getW0().requireHandle(), hp.getW1().requireHandle());
    }
}
