package com.github.magif1712.smarter_touhou_maids.features.smarter.network;

import com.github.magif1712.smarter_touhou_maids.features.smarter.state.MaidSmarterState;
import com.github.magif1712.smarter_touhou_maids.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：sync agent 激活状态（原 smarter 模式开关，语义已变迁）。
 * <p>
 * <b>语义变迁</b>：原由 PossessionPanel 的"启用 Smarter"UI 开关触发；现由
 * {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.SmarterClientService}
 * 检测 {@link com.github.magif1712.smarter_touhou_maids.features.smarter.agent.IAgent#isActive()}
 * 边界变化时触发（agent 激活条件自决：ReflexArcSystemAgent=附身，其他 agent 可直接 true）。
 * <p>
 * smarter 激活时由 {@link com.github.magif1712.smarter_touhou_maids.mixin.MobServerAiStepSuppressMixin}
 * 在 {@code Mob.serverAiStep} HEAD cancel 抑制 TLM 整套 vanilla AI（brain + goalSelector +
 * controls = 脊髓反射），让 MaidActionSink（意识→肌肉）独占实体控制权；失活时 mixin 守卫
 * 自然放行，原版 AI 复原。
 * <p>
 * 设计原则（真善美第 3 条）：服务端 mixin 只读 sync 后的激活标量，不依赖下层激活条件
 * （附身/自动任务等）。换 agent 激活条件时，mixin 零改动——只需 agent isActive 自决 + 本包 sync。
 * <p>
 * 此 packet 仅 sync 激活状态标志到 {@link MaidSmarterState}，不再触碰 {@code NoAi}。
 */
public class ServerboundSetSmarterModePacket {

    private final UUID maidUUID;
    private final boolean enabled;

    public ServerboundSetSmarterModePacket(UUID maidUUID, boolean enabled) {
        this.maidUUID = maidUUID;
        this.enabled = enabled;
    }

    public static void encode(ServerboundSetSmarterModePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUUID);
        buf.writeBoolean(msg.enabled);
    }

    public static ServerboundSetSmarterModePacket decode(FriendlyByteBuf buf) {
        return new ServerboundSetSmarterModePacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(ServerboundSetSmarterModePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            Entity entity = ((ServerLevel) sender.level()).getEntity(msg.maidUUID);
            if (!(entity instanceof EntityMaid maid)) return;
            if (!sender.getUUID().equals(maid.getOwnerUUID())) return;

            MaidSmarterState.setEnabled(maid, msg.enabled);
            // 激活状态 sync：MobServerAiStepSuppressMixin 守卫读取此处的 MaidSmarterState 标志
            // 决定是否 cancel serverAiStep。不再调用 maid.setNoAi(...)——setNoAi(true) 会令
            // isEffectiveAi()=false，导致 LivingEntity.aiStep 跳过 travel()，maid 无法移动。
            // packet handler 的 isEnabled 校验会拦截失活后的残余 ActionIntent 包。
            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> sender),
                new ClientboundSmarterModeSyncPacket(msg.maidUUID, msg.enabled)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
