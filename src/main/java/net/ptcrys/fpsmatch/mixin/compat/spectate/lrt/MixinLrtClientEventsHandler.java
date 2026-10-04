package net.ptcrys.fpsmatch.mixin.compat.spectate.lrt;

import net.ptcrys.fpsmatch.compat.spectate.SpectatorView;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import me.xjqsh.lrtactical.client.ClientEventsHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables LRTactical movement tick animation while spectating.
 */
@Mixin(value = ClientEventsHandler.class, remap = false)
public abstract class MixinLrtClientEventsHandler {

    @Inject(method = "tickAnimation(Lnet/neoforged/neoforge/client/event/ClientTickEvent$Post;)V", at = @At("HEAD"), cancellable = true)
    private static void fpsmatch$skipWhenSpectating(ClientTickEvent.Post event, CallbackInfo ci) {
        if (SpectatorView.isSpectatingOther(Minecraft.getInstance().player)) {
            ci.cancel();
        }
    }
}
