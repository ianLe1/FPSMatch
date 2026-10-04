package net.ptcrys.fpsmatch.common.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.ptcrys.fpsmatch.common.packet.register.PayloadContext;

import java.util.function.Supplier;

public class FPSMSoundPlayS2CPacket {

    ResourceLocation location;

    public FPSMSoundPlayS2CPacket(ResourceLocation location) {
        this.location = location;
    }

    public static void encode(FPSMSoundPlayS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.location);
    }

    public static FPSMSoundPlayS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMSoundPlayS2CPacket(buf.readResourceLocation());
    }

    public void handle(Supplier<PayloadContext> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }

    public ResourceLocation getLocation() {
        return location;
    }
}
