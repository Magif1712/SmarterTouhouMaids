package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.fittable_mapper.nn.original_cnn.containers;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.FloatVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.IntVector;
import java.io.File;

/**
 * CNN 超参（单层）：把"CNN 权重 = 位置 p + 幅度 q + 左/右/偏置 l/r/b + 派生稀疏缓存 idx/w"
 * 这个不实在约束，实在化为一个对象（真善美第4条）。
 * <p>
 * CNN 单层 n→m 映射（与 BNN 单层对称，但权重全为 32-bit float）：
 * <ul>
 *   <li>{@code p_i}（位置，sizeA0）：目标输出位置，连续浮点，范围 [0, sizeA1)。前向时
 *       投影到最近两个输出位 j_0=⌊p⌋ 与 j_1=j_0+1，权重 w_k=1-(p-j_k)²。</li>
 *   <li>{@code q_i}（幅度，sizeA0）：输入分量的缩放系数，范围 [0, 1)。</li>
 *   <li>{@code l_j / r_j / b_j}（左/右/偏置，sizeA1）：邻域耦合与偏置项。</li>
 *   <li>{@code idx0/idx1}（派生索引，IntVector sizeA0）：j_0/j_1 的有效索引（越界记 -1）。</li>
 *   <li>{@code w0/w1}（派生权重，FloatVector sizeA0）：对应 idx 的插值权重。</li>
 * </ul>
 * <p>
 * {@code idx/w} 是 {@code p} 的派生缓存（前向加速用），不是独立权重——
 * {@code save} 只存语义权重 {@code p/q/l/r/b}，{@code loadFromFile} 后由调用方调
 * {@code CnnInferenceOps.cnnRefreshCache} 重算 {@code idx/w}（非热路径，stream 0 + 同步）。
 * <p>
 * 新建时用 PCG 哈希随机填充（{@link FloatVector#fillRandom}）打破零吸引子（与 BNN 同理：
 * 全零权重→零输出→零梯度→永不更新）；五个权重向量用不同子种子（baseSeed + 0..4）避免
 * 同尺寸向量得到相同随机模式。仅此「新建」构造路径随机化；{@code loadFromFile} 走私有构造，
 * 保留磁盘预训练权重不覆盖。
 */
public class CnnHyperparameters implements AutoCloseable {
    private final int sizeA0;
    private final int sizeA1;
    private final FloatVector p;
    private final FloatVector q;
    private final FloatVector l;
    private final FloatVector r;
    private final FloatVector b;
    private final IntVector idx0;
    private final IntVector idx1;
    private final FloatVector w0;
    private final FloatVector w1;

    /**
     * 新建 CNN 超参，PCG 随机初始化 p/q/l/r/b 打破零吸引子。
     * idx/w 未初始化（垃圾值），由调用方调 {@code CnnInferenceOps.cnnRefreshCache} 填充。
     */
    public CnnHyperparameters(int sizeA0, int sizeA1) {
        if (sizeA0 < 0 || sizeA1 < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        this.sizeA0 = sizeA0;
        this.sizeA1 = sizeA1;
        this.p = new FloatVector(sizeA0);
        this.q = new FloatVector(sizeA0);
        this.l = new FloatVector(sizeA1);
        this.r = new FloatVector(sizeA1);
        this.b = new FloatVector(sizeA1);
        this.idx0 = new IntVector(sizeA0);
        this.idx1 = new IntVector(sizeA0);
        this.w0 = new FloatVector(sizeA0);
        this.w1 = new FloatVector(sizeA0);

        long baseSeed = System.nanoTime();
        // p 是目标位置，范围 [0, sizeA1)；随机化后输入分量均匀散射到输出空间各处。
        this.p.fillRandom((float) sizeA1, baseSeed + 0);
        this.q.fillRandom(1.0f, baseSeed + 1);
        this.l.fillRandom(1.0f, baseSeed + 2);
        this.r.fillRandom(1.0f, baseSeed + 3);
        this.b.fillRandom(1.0f, baseSeed + 4);
    }

    private CnnHyperparameters(int sizeA0, int sizeA1,
                               FloatVector p, FloatVector q, FloatVector l, FloatVector r, FloatVector b) {
        this.sizeA0 = sizeA0;
        this.sizeA1 = sizeA1;
        this.p = p;
        this.q = q;
        this.l = l;
        this.r = r;
        this.b = b;
        this.idx0 = new IntVector(sizeA0);
        this.idx1 = new IntVector(sizeA0);
        this.w0 = new FloatVector(sizeA0);
        this.w1 = new FloatVector(sizeA0);
    }

    public int getSizeA0() {
        return sizeA0;
    }

    public int getSizeA1() {
        return sizeA1;
    }

    public FloatVector getP() {
        return p;
    }

    public FloatVector getQ() {
        return q;
    }

    public FloatVector getL() {
        return l;
    }

    public FloatVector getR() {
        return r;
    }

    public FloatVector getB() {
        return b;
    }

    public IntVector getIdx0() {
        return idx0;
    }

    public IntVector getIdx1() {
        return idx1;
    }

    public FloatVector getW0() {
        return w0;
    }

    public FloatVector getW1() {
        return w1;
    }

    /**
     * 将语义权重 p/q/l/r/b 序列化到磁盘。idx/w 不存（派生缓存，loadFromFile 后重算）。
     */
    public void save(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        p.save(new File(folder, "p.bin").getAbsolutePath());
        q.save(new File(folder, "q.bin").getAbsolutePath());
        l.save(new File(folder, "l.bin").getAbsolutePath());
        r.save(new File(folder, "r.bin").getAbsolutePath());
        b.save(new File(folder, "b.bin").getAbsolutePath());
    }

    /**
     * 从磁盘加载语义权重 p/q/l/r/b。idx/w 未初始化，由调用方调
     * {@code CnnInferenceOps.cnnRefreshCache} 重算。
     */
    public static CnnHyperparameters loadFromFile(String folderPath) {
        File folder = new File(folderPath);
        FloatVector p = FloatVector.loadFromFile(new File(folder, "p.bin").getAbsolutePath());
        FloatVector q = FloatVector.loadFromFile(new File(folder, "q.bin").getAbsolutePath());
        FloatVector l = FloatVector.loadFromFile(new File(folder, "l.bin").getAbsolutePath());
        FloatVector r = FloatVector.loadFromFile(new File(folder, "r.bin").getAbsolutePath());
        FloatVector b = FloatVector.loadFromFile(new File(folder, "b.bin").getAbsolutePath());

        int sizeA0 = p.size();
        int sizeA1 = b.size();
        return new CnnHyperparameters(sizeA0, sizeA1, p, q, l, r, b);
    }

    @Override
    public void close() throws Exception {
        if (p != null) {
            p.close();
        }
        if (q != null) {
            q.close();
        }
        if (l != null) {
            l.close();
        }
        if (r != null) {
            r.close();
        }
        if (b != null) {
            b.close();
        }
        if (idx0 != null) {
            idx0.close();
        }
        if (idx1 != null) {
            idx1.close();
        }
        if (w0 != null) {
            w0.close();
        }
        if (w1 != null) {
            w1.close();
        }
    }
}
