package net.ptcrys.fpsmatch.compat.spectate.net;

import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.packet.ClientPacketExecutor;
import net.ptcrys.fpsmatch.common.packet.register.PayloadContext;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packets for TACZ inspect sync while spectating.
 * <p>
 * 已迁移到 NeoForge 1.21 载荷协议：包类改为上游统一的
 * {@code static decode/encode + 实例 handle(Supplier<PayloadContext>)} 协议，
 * 由 {@code NetworkPacketRegister} 反射注册。
 */
public final class SpectatorInspectPackets {

    private SpectatorInspectPackets() {}

    public record C2SStartInspectPacket() {

        public static C2SStartInspectPacket decode(FriendlyByteBuf b) {
            return new C2SStartInspectPacket();
        }

        public static void encode(C2SStartInspectPacket p, FriendlyByteBuf b) {}

        public void handle(Supplier<PayloadContext> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sp = ctx.get().getSender();
                if (sp == null) return;
                S2CWatchedPlayerInspectPacket pkt = new S2CWatchedPlayerInspectPacket(sp.getUUID());
                for (ServerPlayer pl : sp.server.getPlayerList().getPlayers()) {
                    FPSMatch.sendToPlayer(pl, pkt);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record S2CWatchedPlayerInspectPacket(UUID id) {

        public static S2CWatchedPlayerInspectPacket decode(FriendlyByteBuf b) {
            return new S2CWatchedPlayerInspectPacket(b.readUUID());
        }

        public static void encode(S2CWatchedPlayerInspectPacket p, FriendlyByteBuf b) {
            b.writeUUID(p.id);
        }

        public UUID getPlayerId() {
            return this.id;
        }

        public void handle(Supplier<PayloadContext> ctx) {
            ClientPacketExecutor.execute(ctx, this);
        }
    }
}
