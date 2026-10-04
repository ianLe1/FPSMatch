package net.ptcrys.fpsmatch.common.client;

import net.neoforged.fml.common.EventBusSubscriber;
import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.client.data.FPSMClientGlobalData;
import net.ptcrys.fpsmatch.common.client.screen.hud.IHudRenderer;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.LayeredDraw;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import com.google.common.collect.Maps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = FPSMatch.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class FPSMGameHudManager implements LayeredDraw.Layer {

    public static boolean enable = true;
    public static final FPSMGameHudManager INSTANCE = new FPSMGameHudManager();
    private final Map<String, List<IHudRenderer>> gameHudMap = Maps.newHashMap();

    @SubscribeEvent
    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        FPSMClientGlobalData data = FPSMClient.getGlobalData();
        String gameType = data.getCurrentGameType();
        if (enable && INSTANCE.gameHudMap.containsKey(gameType) && !data.isSpectator()) {
            INSTANCE.gameHudMap.get(gameType).forEach(overlay -> overlay.onRenderGuiLayerPre(event));
        }
    }

    public static boolean shouldRender() {
        return enable && FPSMClient.getGlobalData().isInGame();
    }

    public void registerHud(String gameType, IHudRenderer overlay) {
        gameHudMap.computeIfAbsent(gameType, k -> new ArrayList<>()).add(overlay);
    }

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        FPSMClientGlobalData data = FPSMClient.getGlobalData();
        String gameType = data.getCurrentGameType();
        if (enable && gameHudMap.containsKey(gameType)) {
            gameHudMap.get(gameType).forEach(overlay -> overlay.render(guiGraphics, deltaTracker, data.isSpectator()));
        }
    }
}
