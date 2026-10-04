package net.ptcrys.fpsmatch.mixin.compat.spectate.tacz;

import net.ptcrys.fpsmatch.compat.spectate.SpectatorView;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import com.tacz.guns.client.event.TickAnimationEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables TACZ movement tick animation while spectating to avoid local input overrides.
 */
@Mixin(value = TickAnimationEvent.class, remap = false)
public abstract class MixinTaczTickAnimationEvent {

    // TaCZ 1.21.1 把原来单一的 tickAnimation(ClientTickEvent) 拆成两个重载，两条路径都要拦。
    @Inject(method = "tickAnimation(Lnet/neoforged/neoforge/client/event/ClientTickEvent$Pre;)V", at = @At("HEAD"), cancellable = true)
    private static void fpsmatch$skipWhenSpectatingTick(ClientTickEvent.Pre event, CallbackInfo ci) {
        if (SpectatorView.isSpectatingOther(Minecraft.getInstance().player)) {
            ci.cancel();
        }
    }

    @Inject(method = "tickAnimation(Lnet/neoforged/neoforge/client/event/RenderFrameEvent$Post;)V", at = @At("HEAD"), cancellable = true)
    private static void fpsmatch$skipWhenSpectatingFrame(RenderFrameEvent.Post event, CallbackInfo ci) {
        if (SpectatorView.isSpectatingOther(Minecraft.getInstance().player)) {
            ci.cancel();
        }
    }
}
