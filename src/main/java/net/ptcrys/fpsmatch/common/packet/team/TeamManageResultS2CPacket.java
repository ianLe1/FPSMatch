package net.ptcrys.fpsmatch.common.packet.team;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.ptcrys.fpsmatch.common.packet.register.PayloadContext;

import java.util.function.Supplier;

/**
 * 队伍管理操作结果数据包，服务端发送给客户端
 */
public record TeamManageResultS2CPacket(boolean success, Component message) {

    public static void encode(TeamManageResultS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.success());
        ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, packet.message());
    }

    public static TeamManageResultS2CPacket decode(FriendlyByteBuf buf) {
        return new TeamManageResultS2CPacket(buf.readBoolean(), ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf));
    }

    public void handle(Supplier<PayloadContext> ctx) {
        ctx.get().enqueueWork(() -> {});
        ctx.get().setPacketHandled(true);
    }
}
