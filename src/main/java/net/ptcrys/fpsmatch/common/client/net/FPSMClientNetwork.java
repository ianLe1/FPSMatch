package net.ptcrys.fpsmatch.common.client.net;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class FPSMClientNetwork {

    private FPSMClientNetwork() {}

    public static boolean canSendToServer() {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        return listener != null && listener.getConnection().isConnected();
    }
}
