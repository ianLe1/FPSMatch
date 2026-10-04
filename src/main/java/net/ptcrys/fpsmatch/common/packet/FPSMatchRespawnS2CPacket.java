package net.ptcrys.fpsmatch.common.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.ptcrys.fpsmatch.common.packet.register.PayloadContext;

import java.util.function.Supplier;

public class FPSMatchRespawnS2CPacket {

    public static void encode(FPSMatchRespawnS2CPacket packet, FriendlyByteBuf buf) {}

    public static FPSMatchRespawnS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMatchRespawnS2CPacket();
    }

    public void handle(Supplier<PayloadContext> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
