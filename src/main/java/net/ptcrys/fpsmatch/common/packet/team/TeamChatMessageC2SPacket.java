package net.ptcrys.fpsmatch.common.packet.team;

import net.minecraft.network.chat.ComponentSerialization;
import net.ptcrys.fpsmatch.core.FPSMCore;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.ptcrys.fpsmatch.common.packet.register.PayloadContext;

import java.util.function.Supplier;

public record TeamChatMessageC2SPacket(Component message) {

    public static void encode(TeamChatMessageC2SPacket packet, FriendlyByteBuf packetBuffer) {
        ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(packetBuffer, packet.message);
    }

    public static TeamChatMessageC2SPacket decode(FriendlyByteBuf packetBuffer) {
        return new TeamChatMessageC2SPacket(
                ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(packetBuffer));
    }

    public void handle(Supplier<PayloadContext> supplier) {
        PayloadContext context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            FPSMCore.getInstance().getMapByPlayer(player)
                    .flatMap(map -> map.getMapTeams().getTeamByPlayer(player))
                    .ifPresent(team -> team.sendMessage(message));
        });
        context.setPacketHandled(true);
    }
}
