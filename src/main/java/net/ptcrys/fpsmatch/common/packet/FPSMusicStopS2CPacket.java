package net.ptcrys.fpsmatch.common.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.ptcrys.fpsmatch.common.packet.register.PayloadContext;

import java.util.function.Supplier;

public class FPSMusicStopS2CPacket {

    public FPSMusicStopS2CPacket() {}

    public static void encode(FPSMusicStopS2CPacket packet, FriendlyByteBuf buf) {}

    public static FPSMusicStopS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMusicStopS2CPacket();
    }

    public void handle(Supplier<PayloadContext> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
