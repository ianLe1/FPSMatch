package net.ptcrys.fpsmatch.common.client.key;

import net.ptcrys.fpsmatch.common.client.FPSMClient;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.neoforged.fml.common.EventBusSubscriber;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(value = Dist.CLIENT)
public class ClearRenderableAreasKey {

    public static final KeyMapping KEY = new KeyMapping("key.fpsm.clear_renderable_areas.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F12,
            "key.category.fpsm");

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (KEY.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                var debugData = FPSMClient.getGlobalData().getDebugData();
                debugData.toggleVisibility();
                minecraft.player.displayClientMessage(Component.translatable(
                        debugData.isVisible() ? "message.fpsm.preview.shown" : "message.fpsm.preview.hidden"), true);
            }
        }
    }
}
