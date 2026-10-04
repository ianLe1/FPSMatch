package net.ptcrys.fpsmatch.common.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.ptcrys.fpsmatch.common.packet.register.PayloadContext;

import java.util.function.Supplier;

public record FPSMInventorySelectedS2CPacket(int selected) {

    public static void encode(FPSMInventorySelectedS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.selected);
    }

    public static FPSMInventorySelectedS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMInventorySelectedS2CPacket(buf.readInt());
    }

    public void handle(Supplier<PayloadContext> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
