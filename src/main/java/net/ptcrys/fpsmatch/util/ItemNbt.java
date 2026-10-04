package net.ptcrys.fpsmatch.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 1.21.1 移植垫片：{@code ItemStack} 的 NBT 读写已从 {@code getTag()/getOrCreateTag()/setTag()}
 * 改为 {@code DataComponents.CUSTOM_DATA} 组件。
 *
 * <p>本类刻意保持旧 API 的语义——{@link #getOrCreateTag} 返回的 {@link CompoundTag}
 * <b>就是栈上持有的那个实例</b>（{@code CustomData#getUnsafe()} 不复制），因此调用方对它的
 * 原地修改会自动生效，无需额外回写。这与 1.20.1 的行为一致，也避免逐点改写为
 * {@code CustomData.update(...)} 时漏掉某条写路径。</p>
 */
public final class ItemNbt {

    private ItemNbt() {
    }

    /** 等价于 1.20.1 的 {@code ItemStack#getOrCreateTag()}。 */
    public static CompoundTag getOrCreateTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            return data.getUnsafe();
        }
        CompoundTag tag = new CompoundTag();
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return tag;
    }

    /** 等价于 1.20.1 的 {@code ItemStack#getTag()}，没有则返回 {@code null}。 */
    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.getUnsafe();
    }

    /** 等价于 1.20.1 的 {@code ItemStack#setTag(CompoundTag)}，传 {@code null} 即移除。 */
    public static void setTag(ItemStack stack, CompoundTag tag) {
        if (tag == null) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    /** 1.21.1：{@code ItemStack#save} 需要 {@link HolderLookup.Provider}。 */
    public static CompoundTag save(HolderLookup.Provider registries, ItemStack stack) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        Tag tag = stack.save(registries);
        return tag instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    /** 1.21.1：替代已删除的 {@code ItemStack.of(CompoundTag)}。 */
    public static ItemStack parse(HolderLookup.Provider registries, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parseOptional(registries, tag);
    }

    /** 1.21.1：替代已删除的 {@code new ItemStack(Item, int, CompoundTag)}。 */
    public static ItemStack of(Item item, int count, CompoundTag tag) {
        ItemStack stack = new ItemStack(item, count);
        if (tag != null && !tag.isEmpty()) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stack;
    }
}
