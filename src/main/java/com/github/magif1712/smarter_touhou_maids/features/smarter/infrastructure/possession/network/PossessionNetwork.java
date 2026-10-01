package com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.possession.network;

import net.minecraftforge.network.simple.SimpleChannel;

public final class PossessionNetwork {
    private PossessionNetwork() {
    }

    public static int registerPackets(SimpleChannel channel, int baseIndex) {
        int index = baseIndex;
        channel.registerMessage(index++, ServerboundPossessionRequestPacket.class, ServerboundPossessionRequestPacket::encode, ServerboundPossessionRequestPacket::decode, ServerboundPossessionRequestPacket::handle);
        channel.registerMessage(index++, ClientboundPossessionSyncPacket.class, ClientboundPossessionSyncPacket::encode, ClientboundPossessionSyncPacket::decode, ClientboundPossessionSyncPacket::handle);
        channel.registerMessage(index++, ServerboundSetPossessionEnabledPacket.class, ServerboundSetPossessionEnabledPacket::encode, ServerboundSetPossessionEnabledPacket::decode, ServerboundSetPossessionEnabledPacket::handle);
        channel.registerMessage(index++, ClientboundMaidDataSyncPacket.class, ClientboundMaidDataSyncPacket::encode, ClientboundMaidDataSyncPacket::decode, ClientboundMaidDataSyncPacket::handle);
        return index;
    }
}