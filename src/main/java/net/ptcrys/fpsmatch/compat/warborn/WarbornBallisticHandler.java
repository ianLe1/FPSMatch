package net.ptcrys.fpsmatch.compat.warborn;

import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.attributes.ammo.GunDamageHandler;
import net.ptcrys.fpsmatch.common.event.FPSMGunDamageEvent;
import net.ptcrys.fpsmatch.config.FPSMConfig;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import ru.liko.warbornrenewed.content.armorset.WarbornArmorItem;
import ru.liko.warbornrenewed.registry.ModAttributes;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WarBorn Renewed 弹道防护的实际消费者（内层类）。
 * <p>
 * <b>本类是整个 FPSMatch 里唯一引用 {@code ru.liko.warbornrenewed.*} 类型的地方</b>，
 * 并且只由 {@link WarbornCompat} 在 {@code ModList.get().isLoaded("warbornrenewed")}
 * 为真时调用，所以没装 Warborn 的环境下 JVM 永远不会加载它，也就不会
 * {@code NoClassDefFoundError}。
 * </p>
 * <p>
 * 伤害公式完全复用 WarBorn 自带（但在原 mod 内是死代码）的计算方法，未自造公式：
 * <pre>
 *   res        = ModAttributes.getBulletResistance(victim)     // 0~1 减伤比例
 *   pclass     = ModAttributes.getProtectionClass(victim)      // 0~6 防护等级
 *   kinetic    = 0.5 * massKg * v²                             // 估算动能（焦耳）
 *   penetrated = ModAttributes.isPenetrated(pclass, kinetic)   // kinetic > 阈值[pclass]
 *   newBase    = ModAttributes.calculateDamage(baseDamage, res, penetrated)
 * </pre>
 * {@code isPenetrated} 的阈值表（由字节码实证）为
 * {@code {0, 600, 800, 1000, 3500, 5000, 8000}} 焦耳；{@code pclass < 0 || pclass >= 7} 视为穿透。
 * {@code calculateDamage}：未穿透 → {@code base * (1 - res)}；
 * 穿透 → {@code base * (1 - res * 0.5)}（即穿透时仍保留一半减伤）。
 * </p>
 */
final class WarbornBallisticHandler {

    /** 首次真正生效时打一条 INFO，之后不再刷屏。 */
    private static final AtomicBoolean FIRST_HIT_LOGGED = new AtomicBoolean(false);

    /** 判定「子弹速度≈0」的阈值（m/s），低于它就用配置回退速度。 */
    private static final double MIN_BULLET_SPEED_MPS = 1.0D;

    /** Minecraft 1 tick = 1/20 s，实体速度单位是「格/tick」，1 格按 1 米折算。 */
    private static final double TICKS_PER_SECOND = 20.0D;

    private WarbornBallisticHandler() {
    }

    /**
     * 处理一次枪械命中。调用方已保证 Warborn 已加载。
     */
    static void handle(FPSMGunDamageEvent event) {
        if (!FPSMConfig.common.warbornEnabled.get()) {
            return;
        }

        LivingEntity victim = event.getHurtEntity();
        if (victim == null) {
            return;
        }

        // 伤害已被上游清零（出生保护、BlockOffensive 的 TDM 队友免伤等）：
        // 既不参与计算，也不磨损护甲——避免「零伤害命中磨掉护甲」。
        if (event.getBaseAmount() <= 0.0F) {
            return;
        }

        double bulletResistance = ModAttributes.getBulletResistance(victim);
        int protectionClass = ModAttributes.getProtectionClass(victim);
        // 没有任何 Warborn 属性 → 说明没穿 Warborn 护甲，直接不动手
        if (bulletResistance <= 0.0D && protectionClass <= 0) {
            return;
        }

        // 与 FPSMatch 既有 GunDamageHandler.getArmorValue(Player, boolean) 语义对齐：
        // 爆头时必须实际戴着（Warborn）头盔才减伤，否则视为没护住头，不减伤也不磨损。
        boolean headshot = event.isHeadShot();
        if (headshot && !isWarbornArmor(victim.getItemBySlot(EquipmentSlot.HEAD))) {
            return;
        }

        // 同类不误伤：与 GunDamageHandler 完全共用同一判据，避免出现第二套「队友免伤」语义
        if (isSameTeamGunDamage(event, victim)) {
            return;
        }

        double kineticJ = estimateKineticEnergyJoules(event);
        boolean penetrated = ModAttributes.isPenetrated(protectionClass, kineticJ);

        float originalDamage = event.getBaseAmount();
        float newDamage = (float) ModAttributes.calculateDamage(originalDamage, bulletResistance, penetrated);
        event.setBaseAmount(newDamage);

        if (!penetrated && FPSMConfig.common.warbornDamageArmorDurability.get()) {
            int loss = FPSMConfig.common.warbornArmorDurabilityLossPerHit.get();
            if (loss > 0) {
                wearWarbornArmor(victim, headshot, loss);
            }
        }

        if (FIRST_HIT_LOGGED.compareAndSet(false, true)) {
            FPSMatch.LOGGER.info(
                    "[compat] WarBorn Renewed 弹道防护已接入（res={} / class={} / 估算动能={}J / penetrated={}；本次伤害 {} -> {}）",
                    bulletResistance, protectionClass, String.format("%.1f", kineticJ), penetrated,
                    originalDamage, newDamage);
        }
    }

    /**
     * 估算子弹动能（焦耳）。
     * <p>
     * <b>这是估算，不是实证：</b>TaCZ 的 {@code BulletData} 只有 {@code speed}（格/tick）、
     * {@code damageAmount} 等字段，<b>不提供弹头质量、也不提供动能/焦耳</b>。
     * 所以这里按 {@code ½mv²} 估算：
     * </p>
     * <ol>
     *   <li>优先取子弹实体速度：{@code bullet.getDeltaMovement().length() * 20}（格/tick → m/s），
     *       1 格按 1 米折算；</li>
     *   <li>拿不到子弹实体（hitscan 等）或速度≈0 时，回退到配置的 {@code fallbackSpeedMps}；</li>
     *   <li>质量取配置 {@code bulletMassKg}（默认 0.008 kg ≈ 9mm 弹头）。</li>
     * </ol>
     * <p>
     * 速度单位与「服务端是否能在命中时刻读到可信的子弹实体速度」这两点属于假设，
     * 需要实机试玩校准；不同枪包可通过配置调整质量与回退速度。
     * </p>
     */
    private static double estimateKineticEnergyJoules(FPSMGunDamageEvent event) {
        double massKg = FPSMConfig.common.warbornBulletMassKg.get();
        double fallbackSpeedMps = FPSMConfig.common.warbornFallbackSpeedMps.get();

        double speedMps = fallbackSpeedMps;
        Entity bullet = event.getBullet();
        if (bullet != null) {
            Vec3 movement = bullet.getDeltaMovement();
            double measuredMps = movement.length() * TICKS_PER_SECOND;
            if (measuredMps > MIN_BULLET_SPEED_MPS) {
                speedMps = measuredMps;
            }
        }

        return 0.5D * massKg * speedMps * speedMps;
    }

    /**
     * 复用 {@link GunDamageHandler} 的同类判据。
     * <p>
     * 非玩家受击者（例如怪物）不参与 FPSMatch 的队伍逻辑，直接返回 false。
     * </p>
     */
    private static boolean isSameTeamGunDamage(FPSMGunDamageEvent event, LivingEntity victim) {
        if (!(victim instanceof ServerPlayer player)) {
            return false;
        }
        return GunDamageHandler.isSameTeamGunDamage(event, player);
    }

    /**
     * 磨损 Warborn 护甲。爆头只磨损头盔；躯干命中只磨损胸甲。
     * <p>
     * 这里刻意<b>只磨损一件</b>：早期实现遍历 HEAD/CHEST/LEGS/FEET 四个槽位，
     * 会让一发子弹同时打掉整套护甲的耐久（等效 4 倍磨损速度），已修正。
     * </p>
     * <p>
     * 只用 {@link ItemStack#hurtAndBreak(int, LivingEntity, EquipmentSlot)}
     * （该方法内部只在 {@code ServerLevel} 才真正扣耐久，服务端安全）。
     * </p>
     */
    private static void wearWarbornArmor(LivingEntity victim, boolean headshot, int loss) {
        damageIfWarborn(victim, headshot ? EquipmentSlot.HEAD : EquipmentSlot.CHEST, loss);
    }

    private static void damageIfWarborn(LivingEntity victim, EquipmentSlot slot, int loss) {
        ItemStack stack = victim.getItemBySlot(slot);
        if (isWarbornArmor(stack)) {
            stack.hurtAndBreak(loss, victim, slot);
        }
    }

    /** 判定物品是否为 Warborn Renewed 的护甲（{@code WarbornArmorItem}）。 */
    private static boolean isWarbornArmor(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof WarbornArmorItem;
    }
}
