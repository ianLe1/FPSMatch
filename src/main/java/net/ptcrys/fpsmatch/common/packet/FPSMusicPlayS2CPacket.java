package net.ptcrys.fpsmatch.common.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.ptcrys.fpsmatch.common.packet.register.PayloadContext;

import java.util.function.Supplier;

public class FPSMusicPlayS2CPacket {

    ResourceLocation location;

    public FPSMusicPlayS2CPacket(ResourceLocation location) {
        this.location = location;
    }

    public static void encode(FPSMusicPlayS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.location);
    }

    public static FPSMusicPlayS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMusicPlayS2CPacket(buf.readResourceLocation());
    }

    public void handle(Supplier<PayloadContext> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }

    public ResourceLocation getLocation() {
        return location;
    }
}
