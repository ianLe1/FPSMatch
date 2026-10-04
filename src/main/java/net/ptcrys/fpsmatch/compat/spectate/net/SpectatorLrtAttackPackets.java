package net.ptcrys.fpsmatch.compat.spectate.net;

import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.packet.ClientPacketExecutor;
import net.ptcrys.fpsmatch.common.packet.register.PayloadContext;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import me.xjqsh.lrtactical.api.melee.MeleeAction;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packets for LRTactical attack sync while spectating.
 * <p>
 * 已迁移到 NeoForge 1.21 载荷协议，同 {@link SpectatorInspectPackets}。
 */
public final class SpectatorLrtAttackPackets {

    private SpectatorLrtAttackPackets() {}

    public record C2SLrtAttackPacket(MeleeAction action) {

        public static C2SLrtAttackPacket decode(FriendlyByteBuf b) {
            int ordinal = b.readInt();
            MeleeAction[] values = MeleeAction.values();
            if (ordinal < 0 || ordinal >= values.length) {
                return new C2SLrtAttackPacket(MeleeAction.LEFT);
            }
            return new C2SLrtAttackPacket(values[ordinal]);
        }

        public static void encode(C2SLrtAttackPacket p, FriendlyByteBuf b) {
            b.writeInt(p.action.ordinal());
        }

        public void handle(Supplier<PayloadContext> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sp = ctx.get().getSender();
                if (sp == null) return;
                S2CWatchedPlayerLrtAttackPacket pkt = new S2CWatchedPlayerLrtAttackPacket(sp.getUUID(), this.action);
                for (ServerPlayer pl : sp.server.getPlayerList().getPlayers()) {
                    FPSMatch.sendToPlayer(pl, pkt);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record S2CWatchedPlayerLrtAttackPacket(UUID id, MeleeAction action) {

        public static S2CWatchedPlayerLrtAttackPacket decode(FriendlyByteBuf b) {
            UUID playerId = b.readUUID();
            int ordinal = b.readInt();
            MeleeAction[] values = MeleeAction.values();
            MeleeAction action = ordinal >= 0 && ordinal < values.length ? values[ordinal] : MeleeAction.LEFT;
            return new S2CWatchedPlayerLrtAttackPacket(playerId, action);
        }

        public static void encode(S2CWatchedPlayerLrtAttackPacket p, FriendlyByteBuf b) {
            b.writeUUID(p.id);
            b.writeInt(p.action.ordinal());
        }

        public UUID getPlayerId() {
            return this.id;
        }

        public void handle(Supplier<PayloadContext> ctx) {
            ClientPacketExecutor.execute(ctx, this);
        }
    }
}
