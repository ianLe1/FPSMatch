package net.ptcrys.fpsmatch.common.mapselect;

import net.neoforged.fml.common.EventBusSubscriber;
import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.packet.mapselect.MapSelectionAccessS2CPacket;
import net.ptcrys.fpsmatch.config.FPSMConfig;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(modid = FPSMatch.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class MapSelectionAccessSync {

    private MapSelectionAccessSync() {}

    public static boolean canUseMapSelection(ServerPlayer player) {
        return MapRoomQueryService.isMapOperator(player) || FPSMConfig.Server.enableMapSelectionButtonForNonOps.get();
    }

    public static void sync(ServerPlayer player) {
        FPSMatch.sendToPlayer(player, new MapSelectionAccessS2CPacket(canUseMapSelection(player)));
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }
}
