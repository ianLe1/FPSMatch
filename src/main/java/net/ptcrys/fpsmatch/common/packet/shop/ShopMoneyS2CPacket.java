package net.ptcrys.fpsmatch.common.packet.shop;

import net.ptcrys.fpsmatch.common.packet.ClientPacketExecutor;

import net.minecraft.network.FriendlyByteBuf;
import net.ptcrys.fpsmatch.common.packet.register.PayloadContext;

import java.util.UUID;
import java.util.function.Supplier;

public record ShopMoneyS2CPacket(UUID owner, int money) {

    public static void encode(ShopMoneyS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.owner);
        buf.writeInt(packet.money);
    }

    public static ShopMoneyS2CPacket decode(FriendlyByteBuf buf) {
        return new ShopMoneyS2CPacket(
                buf.readUUID(), buf.readInt());
    }

    public void handle(Supplier<PayloadContext> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
