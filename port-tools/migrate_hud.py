#!/usr/bin/env python3
import pathlib, re

ROOT = pathlib.Path(__file__).resolve().parent.parent / 'src/main/java/net/ptcrys/fpsmatch'
miss = []

def sub(rel, pairs):
    p = ROOT / rel
    s = p.read_text(encoding='utf-8'); orig = s
    for a, b in pairs:
        if a not in s:
            miss.append((rel, a[:80])); continue
        s = s.replace(a, b)
    if s != orig:
        p.write_text(s, encoding='utf-8'); print('  ok  ' + rel)

# A. MinecraftForge.EVENT_BUS -> NeoForge.EVENT_BUS
n = 0
for p in ROOT.rglob('*.java'):
    s = p.read_text(encoding='utf-8')
    if 'MinecraftForge' in s:
        s2 = s.replace('MinecraftForge.EVENT_BUS', 'NeoForge.EVENT_BUS').replace('MinecraftForge#EVENT_BUS', 'NeoForge#EVENT_BUS')
        if s2 != s:
            p.write_text(s2, encoding='utf-8'); n += 1
print('  ok  MinecraftForge.EVENT_BUS -> NeoForge.EVENT_BUS：%d 个文件' % n)

# B. 补 EventBusSubscriber import
need = ['common/attributes/ammo/GunDamageHandler.java', 'common/client/camera/CameraEvents.java',
        'common/client/key/ClearRenderableAreasKey.java', 'common/client/key/CustomHudKey.java',
        'common/client/key/SwitchPreviousItemKey.java', 'core/FPSMCore.java']
IMP = 'import net.neoforged.fml.common.EventBusSubscriber;'
for rel in need:
    p = ROOT / rel
    s = p.read_text(encoding='utf-8')
    if IMP in s:
        continue
    lines = s.split('\n')
    idx = max(i for i, l in enumerate(lines) if l.startswith('import '))
    lines.insert(idx + 1, IMP)
    p.write_text('\n'.join(lines), encoding='utf-8')
    print('  ok  +import EventBusSubscriber :: ' + rel)

# C. CameraEvents: RenderGuiOverlayEvent -> RenderGuiLayerEvent
sub('common/client/camera/CameraEvents.java', [
    ('public static void overlay(RenderGuiOverlayEvent.Pre event) {',
     'public static void overlay(RenderGuiLayerEvent.Pre event) {'),
])

# D. IHudRenderer 重写
(ROOT / 'common/client/screen/hud/IHudRenderer.java').write_text('''package net.ptcrys.fpsmatch.common.client.screen.hud;

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
''', encoding='utf-8')
print('  ok  重写 common/client/screen/hud/IHudRenderer.java')

# E. FlashBombHud 重写
(ROOT / 'common/client/screen/hud/FlashBombHud.java').write_text('''package net.ptcrys.fpsmatch.common.client.screen.hud;

import net.ptcrys.fpsmatch.common.effect.FPSMEffectRegister;
import net.ptcrys.fpsmatch.common.effect.FlashBlindnessMobEffect;
import net.ptcrys.fpsmatch.util.RenderUtil;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;

public class FlashBombHud implements LayeredDraw.Layer {

    public static final FlashBombHud INSTANCE = new FlashBombHud();
    public final Minecraft minecraft;

    public FlashBombHud() {
        minecraft = Minecraft.getInstance();
    }

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        LocalPlayer player = minecraft.player;
        if (player != null && player.hasEffect(FPSMEffectRegister.FLASH_BLINDNESS.get())) {
            MobEffectInstance effectInstance = player.getEffect(FPSMEffectRegister.FLASH_BLINDNESS.get());
            if (effectInstance != null && effectInstance.getEffect() instanceof FlashBlindnessMobEffect flashBlindnessMobEffect) {
                float fullBlindnessTime = flashBlindnessMobEffect.getFullBlindnessTime();
                if (fullBlindnessTime > 0) {
                    int colorWithAlpha = RenderUtil.color(255, 255, 255, 255);
                    guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), colorWithAlpha);
                } else {
                    float ticker = flashBlindnessMobEffect.getTicker();
                    if (ticker > 0) {
                        int alpha = (int) (ticker / flashBlindnessMobEffect.getTotalBlindnessTime() * 255);
                        int colorWithAlpha = RenderUtil.color(255, 255, 255, alpha);
                        guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), colorWithAlpha);
                    }
                }
            }
        }
    }
}
''', encoding='utf-8')
print('  ok  重写 common/client/screen/hud/FlashBombHud.java')

# F. FPSMGameHudManager
sub('common/client/FPSMGameHudManager.java', [
    ('import net.neoforged.neoforge.client.event.RenderGuiOverlayEvent;\n'
     'import net.neoforged.neoforge.client.gui.overlay.ForgeGui;\n'
     'import net.neoforged.neoforge.client.gui.overlay.IGuiOverlay;',
     'import net.minecraft.client.DeltaTracker;\n'
     'import net.minecraft.client.gui.LayeredDraw;\n'
     'import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;'),
    ('public class FPSMGameHudManager implements IGuiOverlay {',
     'public class FPSMGameHudManager implements LayeredDraw.Layer {'),
    ('public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {',
     'public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {'),
    ('INSTANCE.gameHudMap.get(gameType).forEach(overlay -> overlay.onRenderGuiOverlayPre(event));',
     'INSTANCE.gameHudMap.get(gameType).forEach(overlay -> overlay.onRenderGuiLayerPre(event));'),
    ('public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {',
     'public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {'),
    ('overlay.render(gui, guiGraphics, partialTick, screenWidth, screenHeight, data.isSpectator())',
     'overlay.render(guiGraphics, deltaTracker, data.isSpectator())'),
])

# G. FPSMClient 注册图层
sub('common/client/FPSMClient.java', [
    ('import net.neoforged.neoforge.client.event.RegisterGuiOverlaysEvent;',
     'import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;'),
    ('import net.neoforged.neoforge.client.gui.overlay.VanillaGuiOverlay;',
     'import net.neoforged.neoforge.client.gui.VanillaGuiLayers;'),
    ('import net.minecraft.world.scores.PlayerTeam;',
     'import net.minecraft.resources.ResourceLocation;\nimport net.minecraft.world.scores.PlayerTeam;'),
    ('public static void onRegisterGuiOverlaysEvent(RegisterGuiOverlaysEvent event) {\n'
     '        event.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(), "flash_bomb_hud", FlashBombHud.INSTANCE);\n'
     '        event.registerBelowAll("hud_manager", FPSMGameHudManager.INSTANCE);\n'
     '    }',
     'public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {\n'
     '        event.registerBelow(VanillaGuiLayers.CHAT,\n'
     '                ResourceLocation.fromNamespaceAndPath(FPSMatch.MODID, "flash_bomb_hud"), FlashBombHud.INSTANCE);\n'
     '        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(FPSMatch.MODID, "hud_manager"),\n'
     '                FPSMGameHudManager.INSTANCE);\n'
     '    }'),
])

print()
if miss:
    print('!!! 未命中 %d 条：' % len(miss))
    for r, a in miss:
        print('   %s :: %s' % (r, a))
else:
    print('HUD 迁移全部命中。')
