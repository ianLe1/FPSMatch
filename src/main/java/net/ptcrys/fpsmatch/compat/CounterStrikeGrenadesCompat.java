package net.ptcrys.fpsmatch.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 【1.21.1 移植桩】counterstrikegrenade (cs-grenade) 没有任何 1.21.1 版本，
 * 上游此类直接引用 club.pisquad.minecraft.csgrenades.* 的 API，导致无法编译。
 *
 * 此处改为「能力缺失」桩：所有查询返回空 / false，注册类方法为空实现。
 * 影响面（已确认）：
 *   - 闪光弹致盲判定（isPlayerFlashed）恒为 false
 *   - 烟雾弹区域判定（isInSmokeGrenadeArea）恒为 false
 *   - 手雷 / 燃烧弹 / 闪光弹的伤害来源 → 物品映射恒为 EMPTY
 *   - 掷弹物子类型注册（init）不会发生
 * 上游的 FlashBangStatsMixin 一并移除（同类依赖）。
 *
 * 恢复路径：若将来出现 1.21.1 版 cs-grenade，把这个文件还原为上游实现，
 * 并把 mixin/compat/grenades/FlashBangStatsMixin.java 与其在 fpsmatch.mixins.json
 * 中的条目一起加回；FPSMatchMixinPlugin.shouldApplyMixin 里
 * "compat.grenades." 的条件分支仍然保留着，无需改动。
 */
public class CounterStrikeGrenadesCompat {

    public static void init() {
        // 能力缺失：无掷弹物可注册
    }

    public static ItemStack getItemFromDamageSource(DamageSource damageSource) {
        return ItemStack.EMPTY;
    }

    public static boolean itemCheck(Player player) {
        return false;
    }

    /** 供 FPSMUtil 使用，替代原先对 CounterStrikeGrenadeItem 的 instanceof。 */
    public static boolean isGrenadeItem(Item item) {
        return false;
    }

    public static boolean isPlayerFlashed(Player player) {
        return false;
    }

    public static boolean isInSmokeGrenadeArea(List<Entity> entities, AABB checker) {
        return false;
    }
}
