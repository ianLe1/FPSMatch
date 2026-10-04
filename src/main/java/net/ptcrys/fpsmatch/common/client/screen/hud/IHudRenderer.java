package net.ptcrys.fpsmatch.common.client.screen.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

public interface IHudRenderer {

    void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event);

    void onSpectatorRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker);

    void onPlayerRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker);

    default void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker, boolean isSpectator) {
        if (isSpectator) {
            onSpectatorRender(guiGraphics, deltaTracker);
        } else {
            onPlayerRender(guiGraphics, deltaTracker);
        }
    }
}
