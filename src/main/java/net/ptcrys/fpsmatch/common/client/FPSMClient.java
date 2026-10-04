package net.ptcrys.fpsmatch.common.client;

import net.neoforged.fml.common.EventBusSubscriber;
import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.client.data.FPSMClientGlobalData;
import net.ptcrys.fpsmatch.common.client.event.FPSMClientResetEvent;
import net.ptcrys.fpsmatch.common.client.key.*;
import net.ptcrys.fpsmatch.common.client.renderer.*;
import net.ptcrys.fpsmatch.common.client.screen.hud.FlashBombHud;
import net.ptcrys.fpsmatch.common.entity.EntityRegister;
import net.ptcrys.fpsmatch.util.RenderUtil;

import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.*;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT, modid = FPSMatch.MODID)
public class FPSMClient {

    private static final FPSMClientGlobalData DATA = new FPSMClientGlobalData();
    public static final Comparator<PlayerInfo> PLAYER_COMPARATOR = Comparator.<PlayerInfo>comparingInt((playerInfo) -> 0)
            .thenComparing((playerInfo) -> Optionull.mapOrDefault(playerInfo.getTeam(), PlayerTeam::getName, ""))
            .thenComparing((playerInfo) -> playerInfo.getProfile().getName(), String::compareToIgnoreCase);

    public static FPSMClientGlobalData getGlobalData() {
        return DATA;
    }

    @SubscribeEvent
    public static void onClientSetup(RegisterKeyMappingsEvent event) {
        // 注册键位
        event.register(CustomHudKey.KEY);
        event.register(SwitchPreviousItemKey.KEY);
        event.register(ClearRenderableAreasKey.KEY);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 注册原版GUI
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CHAT,
                ResourceLocation.fromNamespaceAndPath(FPSMatch.MODID, "flash_bomb_hud"), FlashBombHud.INSTANCE);
        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(FPSMatch.MODID, "hud_manager"),
                FPSMGameHudManager.INSTANCE);
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderEvent(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityRegister.SMOKE_SHELL.get(), new SmokeShellRenderer());
        event.registerEntityRenderer(EntityRegister.INCENDIARY_GRENADE.get(), new IncendiaryGrenadeRenderer());
        event.registerEntityRenderer(EntityRegister.GRENADE.get(), new GrenadeRenderer());
        event.registerEntityRenderer(EntityRegister.FLASH_BOMB.get(), new FlashBombRenderer());
        event.registerEntityRenderer(EntityRegister.MATCH_DROP_ITEM.get(), new MatchDropRenderer());
    }

    public static List<PlayerInfo> getPlayerInfos() {
        if (Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.connection.getListedOnlinePlayers().stream().sorted(PLAYER_COMPARATOR).limit(80L).toList();
        }
        return new ArrayList<>();
    }

    public static void reset() {
        DATA.reset();
        RenderUtil.invalidatePlayerInfoCache();
        NeoForge.EVENT_BUS.post(new FPSMClientResetEvent());
    }
}
