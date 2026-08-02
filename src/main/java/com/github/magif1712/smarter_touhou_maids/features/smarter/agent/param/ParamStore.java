package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.param;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ServerboundSetParamPacket;
import com.github.magif1712.smarter_touhou_maids.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * per-maid 参数持久化与同步仓库（通用层，String 透传）。
 * <p>
 * 把"参数的 NBT 持久化 + 客户端 pending 缓存 + 网络同步"这个不实在的概念（真善美第4条），
 * 实在化为一个类。管道只搬运 String，不感知值类型——
 * 解读（text → long/bool/…）由 factory 经 {@link ParamOption} 的 textProcessor 负责，本类不参与解读。
 * <p>
 * <b>类型无关</b>（真善美第3条）：本类不含任何值类型知识（无 getLong/putLong）。
 * 新增值类型（String/Double/Enum…）时，ParamStore + 网络包零改动——
 * 值类型是管道(X)的模式(Y)，Y 换模态时 X 不改代码。
 * <p>
 * <b>NBT 布局</b>：参数值以 String 存在 maid persistentData → modData → nbtKey。
 * 读取时兼容旧 long 存档（TAG_LONG → String.valueOf 转换）。
 */
@OnlyIn(Dist.CLIENT)
public final class ParamStore {
    public static final ParamStore INSTANCE = new ParamStore();

    /** 客户端 pending 缓存：maidUUID → (nbtKey → value)。避免回显延迟期间读到旧值。 */
    private final HashMap<UUID, Map<String, String>> pendingCache = new HashMap<>();

    private ParamStore() {
    }

    /**
     * 读取 maid 指定 nbtKey 的参数值（String）。先查 pending 缓存，没有再读 maid NBT。
     * 未设置时返回 defaultValue。
     */
    public String getString(EntityMaid maid, String nbtKey, String defaultValue) {
        if (maid == null) return defaultValue;
        Map<String, String> maidCache = pendingCache.get(maid.getUUID());
        if (maidCache != null && maidCache.containsKey(nbtKey)) {
            return maidCache.get(nbtKey);
        }
        return readStringFromNbt(maid, nbtKey, defaultValue);
    }

    /**
     * 设置 maid 指定 nbtKey 的参数值（String）。写 pending 缓存 + 发通用参数同步包。
     */
    public void setString(EntityMaid maid, String nbtKey, String value) {
        if (maid == null) return;
        pendingCache.computeIfAbsent(maid.getUUID(), k -> new HashMap<>()).put(nbtKey, value);
        NetworkHandler.INSTANCE.sendToServer(
            new ServerboundSetParamPacket(maid.getUUID(), nbtKey, value));
    }

    /**
     * 服务端确认同步后回调：更新 pending 缓存 + maid NBT。
     */
    public void onSync(UUID maidUUID, String nbtKey, String value) {
        pendingCache.computeIfAbsent(maidUUID, k -> new HashMap<>()).put(nbtKey, value);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity.getUUID().equals(maidUUID) && entity instanceof EntityMaid maid) {
                    writeStringToNbt(maid, nbtKey, value);
                    return;
                }
            }
        }
    }

    /**
     * 从 maid NBT 读 String。兼容旧 long 存档：若 nbtKey 对应 TAG_LONG 则转 String 返回。
     */
    private static String readStringFromNbt(EntityMaid maid, String nbtKey, String defaultValue) {
        try {
            CompoundTag data = maid.getPersistentData();
            if (data.contains(SmarterTouhouMaids.MOD_ID, 10)) {
                CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
                if (modData.contains(nbtKey, 8)) {  // TAG_STRING
                    return modData.getString(nbtKey);
                }
                // 兼容旧 long 存档（上个版本 dt 参数以 TAG_LONG 存储）
                if (modData.contains(nbtKey, 4)) {  // TAG_LONG
                    return String.valueOf(modData.getLong(nbtKey));
                }
            }
        } catch (Exception ignored) {}
        return defaultValue;
    }

    private static void writeStringToNbt(EntityMaid maid, String nbtKey, String value) {
        CompoundTag data = maid.getPersistentData();
        CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
        modData.putString(nbtKey, value);
        data.put(SmarterTouhouMaids.MOD_ID, modData);
    }
}
