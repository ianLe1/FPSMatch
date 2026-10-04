package net.ptcrys.fpsmatch.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemKey {

    private final Item item;
    private final CompoundTag tag;

    public ItemKey(ItemStack stack) {
        this.item = stack.getItem();
        this.tag = ItemNbt.getTag(stack) != null ? ItemNbt.getTag(stack).copy() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemKey itemKey = (ItemKey) o;

        return ItemStack.isSameItemSameComponents(
                ItemNbt.of(item, 1, tag),
                ItemNbt.of(itemKey.item, 1, itemKey.tag));
    }

    @Override
    public int hashCode() {
        int result = item.hashCode();
        result = 31 * result + (tag != null ? tag.hashCode() : 0);
        return result;
    }
}
