package net.ptcrys.fpsmatch.common.packet.mapselect;

import net.minecraft.network.chat.ComponentSerialization;
import net.ptcrys.fpsmatch.common.packet.ClientPacketExecutor;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.ptcrys.fpsmatch.common.packet.register.PayloadContext;

import java.util.function.Supplier;

public record MapRoomInvitationS2CPacket(String gameType, String mapName, Component message) {

    private static final int ID_MAX_LENGTH = 128;

    public static void encode(MapRoomInvitationS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.gameType(), ID_MAX_LENGTH);
        buf.writeUtf(packet.mapName(), ID_MAX_LENGTH);
        ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, packet.message());
    }

    public static MapRoomInvitationS2CPacket decode(FriendlyByteBuf buf) {
        return new MapRoomInvitationS2CPacket(buf.readUtf(ID_MAX_LENGTH), buf.readUtf(ID_MAX_LENGTH), ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf));
    }

    public void handle(Supplier<PayloadContext> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
