package net.ptcrys.fpsmatch.mixin.ammo;

import net.ptcrys.fpsmatch.compat.IPassThroughEntity;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tacz.guns.entity.EntityKineticBullet;
import me.muksc.tacztweaks.feature.raytracer.BulletHandler;
import me.muksc.tacztweaks.feature.raytracer.BulletRayTracer;
import me.muksc.tacztweaks.feature.datapack.legacy.manager.BulletInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BulletRayTracer.class, remap = false)
public abstract class TweakAmmoMixin {

    @WrapOperation(
                   method = "handle",
                   at = @At(
                            value = "INVOKE",
                            target = "Lme/muksc/tacztweaks/feature/datapack/legacy/manager/BulletInteractionManager;handleBlockInteraction(Lcom/tacz/guns/entity/EntityKineticBullet;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/level/block/state/BlockState;)Lme/muksc/tacztweaks/feature/raytracer/BulletHandler$InteractionResult;"))
    private BulletHandler.InteractionResult fpsmatch$wrapBlockInteraction(BulletInteractionManager instance, EntityKineticBullet entity, BlockHitResult hitResult, BlockState state, Operation<BulletHandler.InteractionResult> original) {
        BulletHandler.InteractionResult interactionResult = original.call(instance, entity, hitResult, state);
        if (entity instanceof IPassThroughEntity throughEntity && interactionResult.getPierce()) {
            throughEntity.fpsmatch$setThroughWall(true);
        }

        return interactionResult;
    }
}
