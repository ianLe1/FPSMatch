package net.ptcrys.fpsmatch.common.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 商店编辑工具的 5x5 容器持有者。
 * <p>
 * NeoForge 1.21.1 移除了 {@code ICapabilityProvider} / {@code LazyOptional} / {@code ForgeCapabilities}，
 * 能力改为在 {@code RegisterCapabilitiesEvent} 里按 {@code ItemCapability} 注册；
 * 同时 {@code ItemStack.getTag()/getOrCreateTag()} 被 {@code DataComponents.CUSTOM_DATA} 取代，
 * {@code ItemStackHandler} 的序列化改为需要 {@code HolderLookup.Provider}。
 * 本类不再实现能力接口，只作为容器持有者 + NBT 读写。
 * </p>
 */
public class EditorShopCapabilityProvider {

    public static final int ROWS = 5;
    public static final int COLS = 5;
    private static final String KEY = "ShopItems";

    private final ItemStack shopEditToolStack;
    private final ItemStackHandler itemStackHandler = new ItemStackHandler(ROWS * COLS) {
        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            save();
        }
    };
    private HolderLookup.Provider registries;

    public EditorShopCapabilityProvider(ItemStack shopEditToolStack) {
        this.shopEditToolStack = shopEditToolStack;
    }

    public ItemStackHandler getItemStackHandler() {
        return itemStackHandler;
    }

    public ItemStack getShopEditToolStack() {
        return shopEditToolStack;
    }

    /** 绑定注册表查询器并载入已存内容（1.21.1 起序列化必须提供 HolderLookup.Provider）。 */
    public void load(HolderLookup.Provider registries) {
        this.registries = registries;
        CustomData data = shopEditToolStack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return;
        }
        CompoundTag tag = data.copyTag();
        if (tag.contains(KEY)) {
            itemStackHandler.deserializeNBT(registries, tag.getCompound(KEY));
        }
    }

    public void save() {
        if (registries == null) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, shopEditToolStack,
                tag -> tag.put(KEY, itemStackHandler.serializeNBT(registries)));
    }
}
