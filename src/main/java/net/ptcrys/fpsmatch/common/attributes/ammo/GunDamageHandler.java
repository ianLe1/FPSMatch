package net.ptcrys.fpsmatch.common.attributes.ammo;

import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.event.FPSMGunDamageEvent;
import net.ptcrys.fpsmatch.common.packet.attribute.BulletproofArmorAttributeS2CPacket;
import net.ptcrys.fpsmatch.config.FPSMConfig;
import net.ptcrys.fpsmatch.core.FPSMCore;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.Optional;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = FPSMatch.MODID)
public class GunDamageHandler {

    @SubscribeEvent
    public static void onEntityHurtByGun(FPSMGunDamageEvent event) {
        if (!(event.getHurtEntity() instanceof ServerPlayer hurtEntity)) {
            return;
        }

        if (isSameTeamGunDamage(event, hurtEntity)) {
            return;
        }

        float baseDamage = event.getBaseAmount();
        boolean headshot = event.isHeadShot();
        if (headshot) {
            // 从配置获取爆头倍率
            float headshotMultiplier = FPSMConfig.common.headshotMultiplier.get().floatValue();
            event.setHeadshotMultiplier(headshotMultiplier);
        }

        float armorValue = getArmorValue(hurtEntity, headshot);
        if (armorValue > 0) {
            // 从配置获取基础穿透系数
            float baseArmorPenetration = FPSMConfig.common.baseArmorPenetration.get().floatValue();
            float finalDamage = baseDamage * (baseArmorPenetration / 2.0F);
            event.setBaseAmount(finalDamage);

            if (finalDamage > hurtEntity.getHealth()) {
                BulletproofArmorAttribute.removePlayer(hurtEntity);
            } else {
                int durabilityReduction = (int) Math.ceil(finalDamage);
                reduceArmorDurability(hurtEntity, durabilityReduction);
            }
        }
    }

    /**
     * 同类（队友）枪械命中判定。
     * <p>
     * 可见性由 private 放宽为 public：Warborn 兼容层（{@code compat/warborn}）复用同一判据，
     * 避免出现第二套「队友免伤」语义。行为本身未做任何改动。
     * </p>
     */
    public static boolean isSameTeamGunDamage(FPSMGunDamageEvent event, ServerPlayer hurtEntity) {
        if (!(event.getAttacker() instanceof ServerPlayer attacker)) {
            return false;
        }
        if (attacker.equals(hurtEntity)) {
            return false;
        }
        return FPSMCore.getInstance().getMapByPlayer(hurtEntity)
                .map(map -> map.getMapTeams().isSameTeam(attacker, hurtEntity))
                .orElse(false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer dead)) {
            return;
        }
        BulletproofArmorAttribute.removePlayer(dead);
    }

    @SubscribeEvent()
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BulletproofArmorAttribute.getInstance(player)
                .ifPresent(attribute -> FPSMatch.sendToPlayer(player, new BulletproofArmorAttributeS2CPacket(attribute)));
    }

    public static int getArmorValue(Player player, boolean headshot) {
        Optional<BulletproofArmorAttribute> optional = BulletproofArmorAttribute.getInstance(player);
        if (optional.isPresent()) {
            BulletproofArmorAttribute attribute = optional.get();
            if (headshot) {
                if (!attribute.hasHelmet()) {
                    return 0;
                } else {
                    return attribute.getDurability();
                }
            } else {
                return attribute.getDurability();
            }
        }
        return 0;
    }

    public static void reduceArmorDurability(ServerPlayer player, int damage) {
        Optional<BulletproofArmorAttribute> optional = BulletproofArmorAttribute.getInstance(player);
        if (optional.isPresent()) {
            BulletproofArmorAttribute attribute = optional.get();
            if (attribute.getDurability() <= damage) {
                BulletproofArmorAttribute.removePlayer(player);
            } else {
                attribute.reduceDurability(damage);
                FPSMatch.sendToPlayer(player, new BulletproofArmorAttributeS2CPacket(attribute));
            }
        }
    }
}
