package net.ptcrys.fpsmatch.common;

import net.neoforged.fml.common.EventBusSubscriber;
import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.event.register.RegisterListenerModuleEvent;
import net.ptcrys.fpsmatch.common.item.MapCreatorTool;
import net.ptcrys.fpsmatch.common.item.SpawnPointTool;
import net.ptcrys.fpsmatch.common.shop.functional.BulletproofArmorWithHelmetListenerModule;
import net.ptcrys.fpsmatch.common.shop.functional.BulletproofArmorWithoutHelmetListenerModule;
import net.ptcrys.fpsmatch.common.shop.functional.ChangeShopItemModule;
import net.ptcrys.fpsmatch.common.shop.functional.ReturnGoodsModule;
import net.ptcrys.fpsmatch.core.FPSMCore;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(modid = FPSMatch.MODID, bus = EventBusSubscriber.Bus.GAME)
public class FPSMEvents {

    @SubscribeEvent
    public static void onServerTickEvent(ServerTickEvent.Post event) {
        FPSMCore.getInstance().onServerTick();
    }

    @SubscribeEvent
    public static void onPlayerTickEvent(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof MapCreatorTool mapCreatorTool) {
            mapCreatorTool.syncHeldPreview(player, stack);
            SpawnPointTool.clearHeldPreview(player);
            return;
        }
        if (stack.getItem() instanceof SpawnPointTool spawnPointTool) {
            spawnPointTool.syncHeldPreview(player, stack);
            MapCreatorTool.clearHeldPreview(player);
            return;
        }

        MapCreatorTool.clearHeldPreview(player);
        SpawnPointTool.clearHeldPreview(player);
    }

    @SubscribeEvent
    public static void onRegisterListenerModuleEvent(RegisterListenerModuleEvent event) {
        event.register(new ReturnGoodsModule());
        ChangeShopItemModule changeShopItemModule = new ChangeShopItemModule(new ItemStack(Items.APPLE), 50, new ItemStack(Items.GOLDEN_APPLE), 300);
        event.register(changeShopItemModule);
        event.register(new BulletproofArmorWithoutHelmetListenerModule());
        event.register(new BulletproofArmorWithHelmetListenerModule());
    }
}
