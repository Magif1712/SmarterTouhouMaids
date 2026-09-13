package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process_original.nn.bnn.containers;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.IntVector;

public class BnnHyperparameters implements AutoCloseable {
    private int sizeA0_original;
    private int sizeA1_original;

    private BoolVector b_original;
    private IntVector p_original;
    private BoolVector q_original;
    private BoolVector l_original;
    private BoolVector r_original;

    public BnnHyperparameters(int sizeA0, int sizeA1) {
        if (sizeA0 < 0 || sizeA1 < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        this.sizeA0_original = sizeA0;
        this.sizeA1_original = sizeA1;

        this.b_original = new BoolVector(sizeA1);
        this.p_original = new IntVector(sizeA0);
        this.q_original = new BoolVector(sizeA0);

        // 如果值向量长度相等或最短的值向量是A0，则l=sizeA0，如果最短的值向量是A1，则l=sizeA1-1
        int sizel = sizeA0 < sizeA1 ? sizeA0 : sizeA1 - 1;
        // 如果值向量长度相等或最短的值向量是A0，则r=sizeA0，如果最短的值向量是A1，则r=sizeA1-1
        int sizer = sizeA1 < sizeA0 ? sizeA1 : sizeA0 - 1;
        this.l_original = new BoolVector(sizel);
        this.r_original = new BoolVector(sizer);

        // 随机初始化权重（打破零吸引子）。
        // BNN 权重若全零则网络确定性死亡：零权重→forward 全零输出→训练 target=自身上轮零输出→
        // 梯度=(0-0)*2=0→权重永不更新。必须随机起步，网络才能产出非零 behavior 并开始学习。
        // 各向量用不同子种子（baseSeed + 偏移），避免同尺寸向量（如 b 与 q 同为 sizeA0/sizeA1 量级）
        // 得到相同的 PCG 随机模式。baseSeed 取 nanoTime，每次附身新建网络都从不同随机态起步。
        // 注意：仅此「新建」构造路径随机化；loadFromFile 走私有构造，保留磁盘预训练权重不覆盖。
        // fillRandom 同步语义：返回即写完，保证 SmarterClientService.init 后续 awaken 启动工作线程时
        // 权重已就绪（init 一次性开销，非 per-tick 热路径）。
        long baseSeed = System.nanoTime();
        this.b_original.fillRandom(baseSeed + 0);
        this.q_original.fillRandom(baseSeed + 1);
        this.l_original.fillRandom(baseSeed + 2);
        this.r_original.fillRandom(baseSeed + 3);
        // p 是目标 bit 索引，范围 [0, sizeA1)；随机化后输入 bit 均匀散射到输出空间各处。
        this.p_original.fillRandom(sizeA1, baseSeed + 4);
    }

    public int getSizeA0() {
        return sizeA0_original;
    }

    public int getSizeA1() {
        return sizeA1_original;
    }

    public BoolVector getB() {
        return b_original;
    }

    public IntVector getP() {
        return p_original;
    }

    public BoolVector getQ() {
        return q_original;
    }

    public BoolVector getL() {
        return l_original;
    }

    public BoolVector getR() {
        return r_original;
    }

    /**
     * 将所有超参数数组序列化到磁盘。
     * 每个数组保存到指定文件夹下的对应文件中。
     */
    public void save(String folderPath) {
        java.io.File folder = new java.io.File(folderPath);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        b_original.save(new java.io.File(folder, "b_original.bin").getAbsolutePath());
        p_original.save(new java.io.File(folder, "p_original.bin").getAbsolutePath());
        q_original.save(new java.io.File(folder, "q_original.bin").getAbsolutePath());
        l_original.save(new java.io.File(folder, "l_original.bin").getAbsolutePath());
        r_original.save(new java.io.File(folder, "r_original.bin").getAbsolutePath());
    }

    /**
     * 从磁盘自动加载超参数，无需手动指定维度。
     */
    public static BnnHyperparameters loadFromFile(String folderPath) {
        java.io.File folder = new java.io.File(folderPath);

        BoolVector b = BoolVector.loadFromFile(new java.io.File(folder, "b_original.bin").getAbsolutePath());
        IntVector p = IntVector.loadFromFile(new java.io.File(folder, "p_original.bin").getAbsolutePath());
        BoolVector q = BoolVector.loadFromFile(new java.io.File(folder, "q_original.bin").getAbsolutePath());
        BoolVector l = BoolVector.loadFromFile(new java.io.File(folder, "l_original.bin").getAbsolutePath());
        BoolVector r = BoolVector.loadFromFile(new java.io.File(folder, "r_original.bin").getAbsolutePath());

        // 从自动加载的数组中反推原始维度
        int sizeA1 = b.size();
        int sizeA0 = p.size();

        return new BnnHyperparameters(sizeA0, sizeA1, b, p, q, l, r);
    }

    private BnnHyperparameters(int sizeA0, int sizeA1, BoolVector b, IntVector p,
                           BoolVector q, BoolVector l, BoolVector r) {
        this.sizeA0_original = sizeA0;
        this.sizeA1_original = sizeA1;
        this.b_original = b;
        this.p_original = p;
        this.q_original = q;
        this.l_original = l;
        this.r_original = r;
    }

    @Override
    public void close() throws Exception {
        if (b_original != null) {
            b_original.close();
        }
        if (p_original != null) {
            p_original.close();
        }
        if (q_original != null) {
            q_original.close();
        }
        if (l_original != null) {
            l_original.close();
        }
        if (r_original != null) {
            r_original.close();
        }
    }
}
