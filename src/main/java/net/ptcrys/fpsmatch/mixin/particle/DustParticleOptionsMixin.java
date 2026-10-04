package net.ptcrys.fpsmatch.mixin.particle;

import net.minecraft.core.particles.DustParticleOptions;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1 的 {@code DustParticleOptionsBase} 在 1.21.1 已被拆掉：
 * {@code DustParticleOptions}/{@code DustColorTransitionOptions} 改为直接继承
 * {@code ScalableParticleOptionsBase}（scale 是该基类的 private final 字段，无法 shadow），
 * 颜色字段则留在各自子类里。故这里只针对 {@code DustParticleOptions} 把 color 变为可变。
 */
@Mixin(DustParticleOptions.class)
public class DustParticleOptionsMixin {

    @Mutable
    @Final
    @Shadow
    private Vector3f color;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(Vector3f vector3f, float f, CallbackInfo ci) {
        this.color = vector3f;
    }
}
