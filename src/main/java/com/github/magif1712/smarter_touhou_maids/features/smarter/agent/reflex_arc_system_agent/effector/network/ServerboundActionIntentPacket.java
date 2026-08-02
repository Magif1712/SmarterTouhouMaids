package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.network;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.ActionIntent;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.effector.execution.MaidActionSink;
import com.github.magif1712.smarter_touhou_maids.features.smarter.network.ServerboundSetParamPacket;
import com.github.magif1712.smarter_touhou_maids.features.smarter.state.MaidSmarterState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：发送效应器解码后的操作要求。
 * <p>
 * 对标 {@link ServerboundSetParamPacket} 的校验模式（owner + smarter mode），
 * 但载荷从 long[] 原始 behavior 换成 {@link ActionIntent}（已解码的具体操作要求）——
 * 解码压力留在客户端，服务端只做轻量执行。
 * <p>
 * 服务端在 {@code enqueueWork}（主线程）直接调用 {@link MaidActionSink#execute} 落地，
 * 频率与发包（20Hz）对齐。无需中转缓冲——enqueueWork 已保证主线程执行。
 * <p>
 * 设计原则（真善美第2条）：意识域 C 中"意识→肌肉"无中转，代码域 D 中 packet handler
 * 直接驱动 MaidActionSink，删除原 MaidActionState 中转层与 SmarterControlGoal 抢占层
 *（C 里没有的"中转"与"flag 抢占"多余模式）。第3条：网络包是"腱"，把客户端肌肉张力
 * 这个不实在的神经信号，用实在的数据包传输固化到服务端。
 * <p>
 * <b>包归属</b>（真善美第3条）：本包是效应器的"腱"——载荷是 {@link ActionIntent}（效应器解码产物），
 * 落地调 {@link MaidActionSink}（效应器执行）。原放 smarter/network/（通用网络层）是错配：效应器特有包
 * 不应在通用层累积。现下放到 effector/network/，与 possession_sensor/possession/network/ 对称——
 * 每个叶子子系统自带 network/ 子包管自己的包，通用 smarter/network/ 只留 smarter 通用语义包
 *（激活态/模式选择/通用参数）。换 effector 模态时新 effector 自带自己的包，通用层零改动。
 */
public class ServerboundActionIntentPacket {

    private final int maidId;
    private final ActionIntent intent;

    public ServerboundActionIntentPacket(int maidId, ActionIntent intent) {
        this.maidId = maidId;
        this.intent = intent;
    }

    public static void encode(ServerboundActionIntentPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.maidId);
        msg.intent.writeTo(buf);
    }

    public static ServerboundActionIntentPacket decode(FriendlyByteBuf buf) {
        int maidId = buf.readVarInt();
        ActionIntent intent = ActionIntent.readFrom(buf);
        return new ServerboundActionIntentPacket(maidId, intent);
    }

    public static void handle(ServerboundActionIntentPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            Entity entity = ((ServerLevel) sender.level()).getEntity(msg.maidId);
            if (!(entity instanceof EntityMaid maid)) return;
            if (!sender.getUUID().equals(maid.getOwnerUUID())) return;
            // smarter 激活态校验（安全门）：仅 smarter 接管的 maid 才允许落地操作要求。
            // MaidSmarterState.isEnabled 是 smarter 通用激活标量（非 effector 特有），跨层共享读取。
            if (!MaidSmarterState.isEnabled(maid)) return;

            // 主线程直接落地：enqueueWork 已保证在服务端主线程执行，无需中转缓冲。
            // smarter 模式由 setNoAi(true) 抑制 TLM brain（脊髓反射），MaidActionSink 独占肌肉。
            MaidActionSink.execute(maid, msg.intent);
        });
        ctx.get().setPacketHandled(true);
    }
}
