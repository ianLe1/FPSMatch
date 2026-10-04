package net.ptcrys.fpsmatch.mixin.compat.lrt;

import net.ptcrys.fpsmatch.compat.LrtUtilityAttribution;

import net.minecraft.world.entity.Entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.xjqsh.lrtactical.entity.sp.SpEffectCloudEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// 1.21.1: Entity#setSecondsOnFire(int) 已改名为 igniteForSeconds(float)
@Mixin(value = SpEffectCloudEntity.class, remap = false)
public abstract class LrtCloudFireStatsMixin {

    @WrapOperation(method = "tick",
                   remap = true,
                   at = @At(value = "INVOKE",
                            target = "Lnet/minecraft/world/entity/Entity;igniteForSeconds(F)V",
                            remap = true))
    private void fpsmatch$rememberThrower(Entity target, float seconds, Operation<Void> original) {
        original.call(target, seconds);
        Entity cloud = (Entity) (Object) this;
        LrtUtilityAttribution.ignited(target, cloud, LrtUtilityAttribution.owner(cloud), Math.round(seconds));
    }
}
