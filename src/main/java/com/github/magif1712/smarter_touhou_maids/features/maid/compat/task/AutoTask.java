package com.github.magif1712.smarter_touhou_maids.features.maid.compat.task;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import com.github.magif1712.smarter_touhou_maids.features.maid.menu.AutoTaskConfigMenu;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.sensor.possession_sensor.possession.config.PossessionConfig;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AutoTask implements IMaidTask {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(SmarterTouhouMaids.MOD_ID, "auto_task");
    private static final String KEY_AUTO_ENABLED = "AutoEnabled";
    private static final String KEY_LOCAL_BOUND = "LocalBound";
    private static final String KEY_POSSESSION_ENABLED = "PossessionEnabled";
    private static final String KEY_FOV = "Fov";
    private static final String KEY_INPUT_POOLS = "InputPools";
    private static final String KEY_OUTPUT_POOLS = "OutputPools";

    public static boolean isPossessionEnabled(EntityMaid maid) {
        try {
            CompoundTag data = maid.getPersistentData();
            if (data.contains(SmarterTouhouMaids.MOD_ID)) {
                CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
                if (modData.contains(KEY_POSSESSION_ENABLED)) {
                    return modData.getBoolean(KEY_POSSESSION_ENABLED);
                }
            }
        } catch (Exception e) { /* ignore */ }
        return PossessionConfig.enabled.get(); // Fallback to global default
    }

    public static void setPossessionEnabled(EntityMaid maid, boolean enabled) {
        CompoundTag persistentData = maid.getPersistentData();
        CompoundTag modData = persistentData.getCompound(SmarterTouhouMaids.MOD_ID);
        modData.putBoolean(KEY_POSSESSION_ENABLED, enabled);
        persistentData.put(SmarterTouhouMaids.MOD_ID, modData);
    }

    // 存储每个女仆的自动任务 3D 数组数据 (EntityID -> Data[x][y][z])
    public static final Map<Integer, int[][][]> AUTO_DATA = new ConcurrentHashMap<>();

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.OBSERVER.getDefaultInstance();
    }

    @Override
    public @Nullable SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        List<Pair<Integer, BehaviorControl<? super EntityMaid>>> tasks = new ArrayList<>();
        try {
            Behavior<EntityMaid> autoBehavior = new Behavior<EntityMaid>(Collections.emptyMap()) {
                @Override
                protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid owner) {
                    try {
                        return isAutoEnabled(owner);
                    } catch (Exception e) {
                        return false;
                    }
                }

                @Override
                protected boolean canStillUse(ServerLevel level, EntityMaid owner, long gameTime) {
                    try {
                        return isAutoEnabled(owner);
                    } catch (Exception e) {
                        return false;
                    }
                }

                @Override
                protected void start(ServerLevel level, EntityMaid owner, long gameTime) {
                    try {
                        owner.getNavigation().stop();
                        owner.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                        owner.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
                    } catch (Exception e) {
                        // Ignore errors during start
                    }
                }


                /**
                 * 女仆工作任务读取 a 并行动 实现位置<br>
                 * 服务端自动任务决策 tick 方法<br>
                 * 每 tick 检查一次 AUTO_DATA 中是否有对应实体ID的数据<br>
                 * 如果有，根据像素值 a 进行移动决策<br>
                 * 像素值 a 小于 85 时前进，大于 170 时后退，85<a<170 时左转，170<a<255 时右转<br>
                 * 同时打印决策信息到服务器日志<br>
                 *
                 * @param level 服务器等级对象
                 * @param owner 女仆实体对象
                 * @param gameTime 当前游戏时间
                 */
                @Override
                protected void tick(ServerLevel level, EntityMaid owner, long gameTime) {
                    try {
                        int[][][] data = AUTO_DATA.get(owner.getId());
                        if (data != null && data.length > 0 && data[0].length > 0 && data[0][0].length > 0) {
                            owner.getNavigation().stop();
                            owner.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                            owner.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
                            int a = data[0][0][0];
                            float speed = 0.5f;
                            float forward = 0.0f;
                            float strafe = 0.0f;
                            String moveType = "";

                            // a<85向上，85<a<170向下，170<a<255向左，其它向右
                            if (a < 85) {
                                forward = speed; // 前进
                                moveType = "前进 (Forward)";
                            } else if (a < 170) {
                                forward = -speed; // 后退
                                moveType = "后退 (Backward)";
                            } else if (a < 255) {
                                strafe = speed; // 向左
                                moveType = "向左 (Left)";
                            } else {
                                strafe = -speed; // 向右
                                moveType = "向右 (Right)";
                            }

                            System.out.println("Maid Auto Decision - Pixel Value: " + a + ", Action: " + moveType); //debug

                            float yaw = owner.getYRot();
                            float yawRad = yaw * Mth.DEG_TO_RAD;
                            Vec3 forwardVec = new Vec3(-Mth.sin(yawRad), 0, Mth.cos(yawRad));
                            Vec3 strafeVec = new Vec3(Mth.cos(yawRad), 0, Mth.sin(yawRad));
                            Vec3 delta = forwardVec.scale(forward).add(strafeVec.scale(strafe));
                            owner.getMoveControl().strafe(strafe, forward);
                            owner.setDeltaMovement(delta.x, owner.getDeltaMovement().y, delta.z);
                            owner.hasImpulse = true;
                            owner.setYHeadRot(owner.getYRot());
                            owner.setYBodyRot(owner.getYRot());
                            owner.setXRot(0.0f);
                            owner.setYRot(owner.getYRot());
                        } else {
                            owner.getMoveControl().strafe(0.0f, 0.0f);
                            owner.setZza(0.0f);
                            owner.setXxa(0.0f);
                            owner.setDeltaMovement(0.0, owner.getDeltaMovement().y, 0.0);
                        }
                    } catch (Exception e) {
                        // Ignore errors during tick
                    }
                }

                @Override
                protected void stop(ServerLevel level, EntityMaid owner, long gameTime) {
                    try {
                        owner.getNavigation().stop();
                        owner.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                        owner.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
                        owner.getMoveControl().strafe(0.0f, 0.0f);
                        owner.setZza(0.0f);
                        owner.setXxa(0.0f);
                        owner.setDeltaMovement(0.0, owner.getDeltaMovement().y, 0.0);
                    } catch (Exception ignored) {
                    }
                }
            };

            tasks.add(Pair.of(0, autoBehavior));
        } catch (Exception e) {
            e.printStackTrace();
            // 如果创建任务失败，返回空列表，确保女仆能正常生成
        }
        return tasks;
    }

    @Override
    public @Nullable MenuProvider getTaskConfigGuiProvider(EntityMaid maid) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("menu.smarter_touhou_maids.auto_task_config");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                return new AutoTaskConfigMenu(id, inventory, maid);
            }
        };
    }

    public static void handleConfigUpdate(ServerPlayer sender, EntityMaid maid, int operationType, String data) {
        CompoundTag persistentData = maid.getPersistentData();
        CompoundTag modData = persistentData.getCompound(SmarterTouhouMaids.MOD_ID);

        switch (operationType) {
            case 0: // Toggle Auto
                boolean current = modData.getBoolean(KEY_AUTO_ENABLED);
                boolean enabled = !current;
                modData.putBoolean(KEY_AUTO_ENABLED, enabled);
                persistentData.put(SmarterTouhouMaids.MOD_ID, modData);
                if (!enabled) {
                    AUTO_DATA.remove(maid.getId());
                    maid.getNavigation().stop();
                    maid.getMoveControl().strafe(0.0f, 0.0f);
                    maid.setZza(0.0f);
                    maid.setXxa(0.0f);
                    maid.setDeltaMovement(0.0, maid.getDeltaMovement().y, 0.0);
                }
                break;
            case 1: {
                String[] parts = data.split("\\|", 2);
                String poolId = parts.length > 0 ? parts[0].trim() : "";
                String secret = parts.length > 1 ? parts[1].trim() : "";
                if (poolId.isEmpty() || !isPoolSecretValid(maid, poolId, secret)) {
                    sender.sendSystemMessage(Component.translatable("msg.smarter_touhou_maids.pool_secret_invalid"));
                    break;
                }
                ListTag list = modData.getList(KEY_INPUT_POOLS, Tag.TAG_STRING);
                boolean exists = false;
                for (int i = 0; i < list.size(); i++) {
                    if (list.getString(i).equals(poolId)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    list.add(StringTag.valueOf(poolId));
                    modData.put(KEY_INPUT_POOLS, list);
                    persistentData.put(SmarterTouhouMaids.MOD_ID, modData);
                }
                break;
            }
            case 2: {
                String poolId = data.trim();
                ListTag list = modData.getList(KEY_INPUT_POOLS, Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) {
                    if (list.getString(i).equals(poolId)) {
                        list.remove(i);
                        break;
                    }
                }
                modData.put(KEY_INPUT_POOLS, list);
                persistentData.put(SmarterTouhouMaids.MOD_ID, modData);
                break;
            }
            case 3: {
                String poolId = data.trim();
                if (poolId.isEmpty()) {
                    break;
                }
                updateAllMaidsOutputPools(sender.server, sender.getUUID(), poolId, true);
                break;
            }
            case 4: {
                String poolId = data.trim();
                updateAllMaidsOutputPools(sender.server, sender.getUUID(), poolId, false);
                break;
            }
            case 5: // Bind Local
                modData.putBoolean(KEY_LOCAL_BOUND, true);
                persistentData.put(SmarterTouhouMaids.MOD_ID, modData);
                break;
            case 6: // Unbind Local
                modData.putBoolean(KEY_LOCAL_BOUND, false);
                persistentData.put(SmarterTouhouMaids.MOD_ID, modData);
                break;
            case 7: // FOV
                try {
                    modData.putInt(KEY_FOV, Integer.parseInt(data));
                    persistentData.put(SmarterTouhouMaids.MOD_ID, modData);
                } catch (NumberFormatException e) {
                    // Ignore invalid input
                }
                break;
        }
    }

    private static void updateAllMaidsOutputPools(MinecraftServer server, UUID playerUUID, String poolId, boolean add) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityMaid maid) {
                    if (playerUUID.equals(maid.getOwnerUUID())) {
                        CompoundTag data = maid.getPersistentData();
                        CompoundTag modData = data.getCompound(SmarterTouhouMaids.MOD_ID);
                        ListTag list = modData.getList(KEY_OUTPUT_POOLS, Tag.TAG_STRING);
                        if (add) {
                            boolean exists = false;
                            for (int i = 0; i < list.size(); i++) {
                                if (list.getString(i).equals(poolId)) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists && !poolId.isEmpty()) {
                                list.add(StringTag.valueOf(poolId));
                            }
                        } else {
                            for (int i = 0; i < list.size(); i++) {
                                if (list.getString(i).equals(poolId)) {
                                    list.remove(i);
                                    break;
                                }
                            }
                        }
                        modData.put(KEY_OUTPUT_POOLS, list);
                        data.put(SmarterTouhouMaids.MOD_ID, modData);
                    }
                }
            }
        }
    }

    public static boolean isAutoEnabled(EntityMaid maid) {
        try {
            CompoundTag data = maid.getPersistentData();
            if (data.contains(SmarterTouhouMaids.MOD_ID)) {
                return data.getCompound(SmarterTouhouMaids.MOD_ID).getBoolean(KEY_AUTO_ENABLED);
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    /**
     * maid 的当前任务模式是否为"自动任务"（auto_task）。
     * <p>
     * 这是 smarter 就绪的通用条件——玩家将女仆任务模式切到"自动任务"即 smarter 就绪。
     * 经 TLM 的 {@code SynchedEntityData}（{@code DATA_TASK}）自动双端同步，客户端可靠读取，
     * 不依赖 Forge {@code persistentData}（不同步）。
     * <p>
     * 区别于 {@link #isAutoEnabled}：后者是 auto_task 内部"自动启用"开关，存 persistentData，
     * 不双端同步，仅服务端 brain 用（且当前为死代码——无写入入口）。
     * 故 smarter 就绪判断应用本方法（task 本身），而非 isAutoEnabled。
     */
    public static boolean isAutoTask(EntityMaid maid) {
        return maid != null && AutoTask.UID.equals(maid.getTask().getUid());
    }

    public static boolean isLocalBound(EntityMaid maid) {
        try {
            CompoundTag data = maid.getPersistentData();
            if (data.contains(SmarterTouhouMaids.MOD_ID)) {
                return data.getCompound(SmarterTouhouMaids.MOD_ID).getBoolean(KEY_LOCAL_BOUND);
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    public static int getFov(EntityMaid maid) {
        try {
            CompoundTag data = maid.getPersistentData();
            if (data.contains(SmarterTouhouMaids.MOD_ID)) {
                int fov = data.getCompound(SmarterTouhouMaids.MOD_ID).getInt(KEY_FOV);
                return fov == 0 ? 70 : fov;
            }
        } catch (Exception e) {
            // ignore
        }
        return 70;
    }

    public static List<String> getInputPools(EntityMaid maid) {
        try {
            CompoundTag data = maid.getPersistentData();
            List<String> list = new ArrayList<>();
            if (data.contains(SmarterTouhouMaids.MOD_ID)) {
                ListTag tagList = data.getCompound(SmarterTouhouMaids.MOD_ID).getList(KEY_INPUT_POOLS, Tag.TAG_STRING);
                for (int i = 0; i < tagList.size(); i++) {
                    list.add(tagList.getString(i));
                }
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static List<String> getOutputPools(EntityMaid maid) {
        try {
            CompoundTag data = maid.getPersistentData();
            List<String> list = new ArrayList<>();
            if (data.contains(SmarterTouhouMaids.MOD_ID)) {
                ListTag tagList = data.getCompound(SmarterTouhouMaids.MOD_ID).getList(KEY_OUTPUT_POOLS, Tag.TAG_STRING);
                for (int i = 0; i < tagList.size(); i++) {
                    list.add(tagList.getString(i));
                }
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static boolean isPoolSecretValid(EntityMaid maid, String poolId, String secret) {
        if (secret.isEmpty()) {
            return false;
        }
        List<String> outputPools = getOutputPools(maid);
        if (!outputPools.contains(poolId)) {
            return false;
        }
        return secret.equals(poolId);
    }
}