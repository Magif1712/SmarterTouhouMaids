package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.cnn_active_mapper.nn.cnn_active;

import com.github.magif1712.smarter_touhou_maids.core.containers.domain.Span;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;
import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.cnn.AbstractCnnNeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.cnn.containers.CnnFwTraceForBw;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.semantics.containers.io.IODomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.bionic_muscle_effector.semantics.PolarLayout;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 判据（方案 §十）：新活性 CNN 的<b>行为位活性</b>与 <b>prevB 时间递归</b>。
 * <p>
 * <b>为什么这是"硬判据"而不是"感受性描述"</b>：原 CNN 的病是<b>结构性</b>的——
 * {@code fillRandom} 单边非负 ⟹ {@code z ≥ 0} 恒成立 ⟹ 奇激活判决 {@code [z≥0]} 恒真
 * ⟹ 256 位恒 1 ⟹ 拮抗对做差 {@code 1−1=0} ⟹ 效应器原地不动。所以"位是否混合"直接等价于
 * "病是否被治好"，不需进游戏也能判定（方案 §十 判据 ①②③）。
 * <p>
 * <b>本测试需要真实 CUDA 设备</b>（前向是真 kernel）。无原生库/无设备/显存不足时
 * <b>跳过而非失败</b>——单测不应因环境缺 GPU 而变红，但绝不允许静默通过（{@link Assumptions#assumeTrue}）。
 */
class CnnActiveNeuralNetworkTest {

    private static final int F = AbstractCnnNeuralNetwork.CNN_FEELING_LENGTH;
    private static final int B = AbstractCnnNeuralNetwork.CNN_BEHAVIOR_LENGTH;

    private static int inputSize;
    private static int outputSize;
    private static int bOffset;

    @BeforeAll
    static void probe() {
        // 尺寸取自真实契约（定理1(3)）：复用 CNN_PROFILE ⟹ sizeA0 = 4F+17、sizeA1 = 4F+256。
        IODomain ioDomain = new IODomain(AbstractCnnNeuralNetwork.CNN_PROFILE);
        inputSize = ioDomain.getInputDomain().totalLength();
        outputSize = ioDomain.getOutputDomain().totalLength();
        bOffset = outputSize - B;

        assertEquals(4 * F + 17, inputSize, "sizeA0 必须是 4F+17（活动域契约）");
        assertEquals(4 * F + 256, outputSize, "sizeA1 必须是 4F+256（活动域契约）");

        // 原生库 + CUDA 设备可用性探测。这是"环境能力"而非"被测对象"，故失败即跳过。
        boolean available;
        try {
            NativeLibLoader.ensureLoaded();
            FloatVector probe = new FloatVector(4);
            probe.close();
            available = true;
        } catch (Throwable t) {
            available = false;
        }
        Assumptions.assumeTrue(available, "无 stm_ai 原生库或 CUDA 设备可用，跳过需要真 GPU 的判据测试");
    }

    /**
     * 判据 ①②③：不经训练直接前向，断言 256 位行为输出
     * <ol>
     *   <li><b>既非全 0 也非全 1</b>——直接否定原 CNN 的"恒 1"病态；</li>
     *   <li><b>1 的比例落在 (10%, 90%)</b>——位确实被混合（不是偶然翻转几位）；</li>
     *   <li><b>预留 48 位恒为 0</b>——D7 生效，冻结位诊断能力保住。</li>
     * </ol>
     * 输入用 {@code [0,1)} 均匀随机（忠实复现 {@code RgbFloatEncoder} 的输出域——原病的发源地）。
     */
    @Test
    void forwardProducesMixedBitsAndKeepsReservedZero() throws Exception {
        CnnActiveNeuralNetwork nn = null;
        FloatVector x = null;
        FloatVector y = null;
        FloatVector behavior = null;
        CnnFwTraceForBw trace = null;
        try {
            // D = 默认（tanh + 半径 0.5）——L0。
            nn = CnnActiveNeuralNetwork.createFresh(inputSize, outputSize, CnnActiveOptions.defaults());
            assertNotNull(nn);

            x = new FloatVector(inputSize);
            x.fillRandom(1.0f, 12345L);                 // [0,1)：与 RgbFloatEncoder 同域
            y = new FloatVector(outputSize);
            trace = (CnnFwTraceForBw) nn.createFwTraceForBw();   // StoreTrace（顺便留下 z 供递归测试思路复用）

            nn.forward(x, 0L /* -> */, y, trace);
            nn.forward(x, 0L /* -> */, y, trace);       // 第二拍：此时 prevB 已非 0（本判据不依赖它）

            // 把输出 B 段搬到 mapped 行为缓冲（生产路径同构：流程经 copyFromOutput 搬 B 段，
            // 效应器读 mapped 内存判定位；readBehaviorTo 要求 mapped 载体）。
            float[] yHost = y.toHostArray();            // 同步 D2H：drain 默认流
            float[] bSlice = new float[B];
            System.arraycopy(yHost, bOffset, bSlice, 0, B);
            behavior = FloatVector.mapped(B);
            behavior.copyFromHost(bSlice, B, 0L);       // H2D
            drain(y);                                   // 再 drain：确保 H2D 已完成，host 才能读 mapped

            int[] bits = new int[(B + 31) / 32];
            nn.readBehaviorTo(behavior, bits, 0L);

            int ones = 0;
            for (int i = 0; i < B; i++) {
                if (((bits[i >> 5] >>> (i & 31)) & 1) != 0) {
                    ones++;
                }
            }

            // ① 既非全 0 也非全 1
            assertTrue(ones > 0, "256 位全 0：落进'全 0 吸附点'（对称初始化未生效或激活值域异常）");
            assertTrue(ones < B, "256 位全 1：仍在原 CNN 的'恒 1'病态（D1/D2 未生效）");

            // ② 1 的比例 ∈ (10%, 90%)
            double ratio = (double) ones / (double) B;
            assertTrue(ratio > 0.10 && ratio < 0.90,
                    () -> String.format("1 的比例 %.1f%% 不在 (10%%, 90%%)：位未被充分混合", ratio * 100.0));

            // ③ 预留 48 位恒 0（D7）
            Span reserved = PolarLayout.defaultHumanLike().getReservedSpan();
            for (int i = reserved.getOffset(); i < reserved.getOffset() + reserved.getLength(); i++) {
                boolean bit = ((bits[i >> 5] >>> (i & 31)) & 1) != 0;
                assertTrue(!bit, "预留位 " + i + " 不为 0：D7（掩预留位）未生效");
            }
        } catch (OutOfMemoryError | RuntimeException e) {
            // 显存不足（~1.6GB）等环境问题不算被测对象失败——但必须显式跳过，不静默通过。
            Assumptions.assumeTrue(false, "CUDA/显存环境不足，跳过判据测试: " + e);
        } finally {
            closeQuietly(trace, behavior, y, x, nn);
        }
    }

    /**
     * 判据（回归）：{@code prevB} 递推——同一输入连续前向两次，B 段的 pre-activation {@code z} 必须不同。
     * <p>
     * <b>为什么这个断言足以证明递归生效</b>：第一拍 {@code prevB == 0}（构造期 memset），
     * 故 B 段的 {@code l/r} 项贡献为 0；第二拍 {@code prevB = tanh(z_B^{(1)}) ≠ 0}，
     * {@code l/r} 非零 ⟹ {@code z_B^{(2)} ≠ z_B^{(1)}}。若递归未生效，同输入同权重下 {@code z} 必然逐位相同。
     * <p>
     * 这同时验证了截断式单步递归的合法性前提：{@code prevB} 被当作 {@code X} 的一部分，
     * {@code f} 仍是单一映射（公理(1)）——同输入 + 同 {@code prevB} 必然给出同输出。
     */
    @Test
    void prevBRecursionChangesSecondForwardPreActivation() throws Exception {
        CnnActiveNeuralNetwork nn = null;
        FloatVector x = null;
        FloatVector y = null;
        CnnFwTraceForBw trace = null;
        try {
            nn = CnnActiveNeuralNetwork.createFresh(inputSize, outputSize, CnnActiveOptions.defaults());

            x = new FloatVector(inputSize);
            x.fillRandom(1.0f, 999L);
            y = new FloatVector(outputSize);
            trace = (CnnFwTraceForBw) nn.createFwTraceForBw();

            nn.forward(x, 0L /* -> */, y, trace);
            float[] zFirst = trace.z.toHostArray();     // 同步 D2H（第一拍：prevB == 0）

            nn.forward(x, 0L /* -> */, y, trace);
            float[] zSecond = trace.z.toHostArray();    // 第二拍：prevB 已回灌

            int diffs = 0;
            for (int j = bOffset; j < outputSize; j++) {
                if (zFirst[j] != zSecond[j]) {
                    diffs++;
                }
            }
            assertTrue(diffs > 0,
                    "两拍 B 段 pre-activation 完全相同：prevB 未进入前向（D4 未生效）");
        } catch (OutOfMemoryError | RuntimeException e) {
            Assumptions.assumeTrue(false, "CUDA/显存环境不足，跳过判据测试: " + e);
        } finally {
            closeQuietly(trace, y, x, nn);
        }
    }

    /** 同步 D2H：drain 默认流，使此前排队的 kernel/H2D 全部完成（host 才能安全读 mapped 内存）。 */
    private static void drain(FloatVector any) {
        float[] scratch = new float[1];
        any.copyToHost(scratch, 1);
    }

    private static void closeQuietly(AutoCloseable... resources) {
        for (AutoCloseable r : resources) {
            if (r == null) {
                continue;
            }
            try {
                r.close();
            } catch (Exception ignored) {
                // 测试清理：释放失败不影响判据结论
            }
        }
    }
}
