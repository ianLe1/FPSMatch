package net.ptcrys.fpsmatch.common.packet.mapselect;

import net.minecraft.network.chat.ComponentSerialization;
import net.ptcrys.fpsmatch.common.packet.ClientPacketExecutor;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.ptcrys.fpsmatch.common.packet.register.PayloadContext;

import java.util.function.Supplier;

public record MapRoomToastS2CPacket(Component message, boolean error, long requestId) {

    public MapRoomToastS2CPacket(Component message, boolean error) {
        this(message, error, -1L);
    }

    public static void encode(MapRoomToastS2CPacket packet, FriendlyByteBuf buf) {
        ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, packet.message());
        buf.writeBoolean(packet.error());
        buf.writeLong(packet.requestId());
    }

    public static MapRoomToastS2CPacket decode(FriendlyByteBuf buf) {
        return new MapRoomToastS2CPacket(ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf), buf.readBoolean(), buf.readLong());
    }

    public void handle(Supplier<PayloadContext> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
