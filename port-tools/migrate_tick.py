#!/usr/bin/env python3
# FPSMatch 1.21.1 移植：TickEvent / 依赖 迁移脚本
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / 'src/main/java/net/ptcrys/fpsmatch'
miss = []


def sub(rel, pairs):
    p = ROOT / rel
    s = p.read_text(encoding='utf-8')
    orig = s
    for a, b in pairs:
        if a not in s:
            miss.append((rel, a[:90]))
            continue
        s = s.replace(a, b)
    if s != orig:
        p.write_text(s, encoding='utf-8')
        print('  ok  ' + rel)


sub('common/FPSMEvents.java', [
    ('import net.neoforged.neoforge.event.TickEvent;',
     'import net.neoforged.neoforge.event.tick.PlayerTickEvent;\nimport net.neoforged.neoforge.event.tick.ServerTickEvent;'),
    ('public static void onServerTickEvent(TickEvent.ServerTickEvent event) {\n'
     '        if (event.phase == TickEvent.Phase.END) {\n'
     '            FPSMCore.getInstance().onServerTick();\n'
     '        }\n'
     '    }',
     'public static void onServerTickEvent(ServerTickEvent.Post event) {\n'
     '        FPSMCore.getInstance().onServerTick();\n'
     '    }'),
    ('public static void onPlayerTickEvent(TickEvent.PlayerTickEvent event) {\n'
     '        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {',
     'public static void onPlayerTickEvent(PlayerTickEvent.Post event) {\n'
     '        if (!(event.getEntity() instanceof ServerPlayer player)) {'),
])

sub('common/client/FPSMClientEvents.java', [
    ('import net.neoforged.neoforge.event.TickEvent;',
     'import net.neoforged.neoforge.client.event.ClientTickEvent;'),
    ('public static void onClientTick(TickEvent.ClientTickEvent event) {',
     'public static void onClientTick(ClientTickEvent.Post event) {'),
    ('if (event.phase == TickEvent.Phase.END) FPSMClientPacketHandlers.flushPendingTeamPlayerStats();',
     'FPSMClientPacketHandlers.flushPendingTeamPlayerStats();'),
    ('if (event.phase == TickEvent.Phase.END && SpectateState.isRestricted()',
     'if (SpectateState.isRestricted()'),
    ('ClientPlayerPayloadContext', 'ClientPlayerNetworkEvent'),
])

sub('common/client/camera/CameraEvents.java', [
    ('import net.neoforged.neoforge.event.TickEvent;',
     'import net.neoforged.neoforge.client.event.ClientTickEvent;\nimport net.neoforged.neoforge.client.event.RenderFrameEvent;'),
    ('public static void validate(TickEvent.ClientTickEvent event) {\n'
     '        if (event.phase == TickEvent.Phase.START) CameraDirector.validate();',
     'public static void validate(ClientTickEvent.Pre event) {\n'
     '        CameraDirector.validate();'),
    ('public static void tick(TickEvent.ClientTickEvent event) {\n'
     '        if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().isPaused()) CameraDirector.tick();',
     'public static void tick(ClientTickEvent.Post event) {\n'
     '        if (!Minecraft.getInstance().isPaused()) CameraDirector.tick();'),
    ('public static void frame(TickEvent.RenderTickEvent event) {\n'
     '        if (event.phase == TickEvent.Phase.START) {\n'
     '            ++renderFrame;\n'
     '            CameraDirector.prepareFrame(event.renderTickTime);\n'
     '        }\n'
     '    }',
     'public static void frame(RenderFrameEvent.Pre event) {\n'
     '        ++renderFrame;\n'
     '        CameraDirector.prepareFrame(event.getPartialTick().getGameTimeDeltaPartialTick(false));\n'
     '    }'),
    ('ClientPlayerPayloadContext', 'ClientPlayerNetworkEvent'),
])

sub('common/client/key/ClearRenderableAreasKey.java', [
    ('import net.neoforged.neoforge.event.TickEvent;',
     'import net.neoforged.neoforge.client.event.ClientTickEvent;'),
    ('public static void onClientTick(TickEvent.ClientTickEvent event) {\n'
     '        if (event.phase != TickEvent.Phase.END) return;',
     'public static void onClientTick(ClientTickEvent.Post event) {'),
])

sub('common/client/key/SwitchPreviousItemKey.java', [
    ('import net.neoforged.neoforge.event.TickEvent;',
     'import net.neoforged.neoforge.client.event.ClientTickEvent;'),
    ('public static void onClientTick(TickEvent.ClientTickEvent event) {\n'
     '        if (event.phase == TickEvent.Phase.END) {',
     'public static void onClientTick(ClientTickEvent.Post event) {\n'
     '        {'),
])

sub('common/effect/FlashBlindnessMobEffect.java', [
    ('import net.neoforged.neoforge.event.TickEvent;',
     'import net.neoforged.neoforge.event.tick.PlayerTickEvent;'),
    ('public static void onServerTickEvent(TickEvent.PlayerTickEvent event) {\n'
     '        if (event.phase == TickEvent.Phase.END) {',
     'public static void onServerTickEvent(PlayerTickEvent.Post event) {\n'
     '        {'),
    ('event.player', 'event.getEntity()'),
])

sub('common/event/FPSMDeathPipelineEventHook.java', [
    ('import net.neoforged.neoforge.event.TickEvent;',
     'import net.neoforged.neoforge.event.tick.ServerTickEvent;'),
    ('public static void onServerTick(TickEvent.ServerTickEvent event) {\n'
     '        if (event.phase != TickEvent.Phase.END) {\n'
     '            return;\n'
     '        }\n',
     'public static void onServerTick(ServerTickEvent.Post event) {\n'),
])

for rel, var in [
    ('compat/spectate/tacz/SpectatorGunFireMirror.java', 'e'),
    ('compat/spectate/tacz/SpectatorGunItemMirrorTicker.java', 'event'),
    ('compat/spectate/tacz/SpectatorGunMovementMirror.java', 'event'),
]:
    sub(rel, [
        ('import net.neoforged.neoforge.event.TickEvent;',
         'import net.neoforged.neoforge.client.event.ClientTickEvent;'),
        ('public static void onClientTick(TickEvent.ClientTickEvent %s) {\n'
         '        if (%s.phase != TickEvent.Phase.END) {\n'
         '            return;\n'
         '        }\n' % (var, var),
         'public static void onClientTick(ClientTickEvent.Post %s) {\n' % var),
    ])

sub('compat/tacz/client/event/SpectatorEventHandler.java', [
    ('import net.neoforged.neoforge.event.TickEvent;',
     'import net.neoforged.neoforge.client.event.ClientTickEvent;'),
    ('public static void onClientTick(TickEvent.ClientTickEvent event) {\n'
     '        if (event.phase != TickEvent.Phase.END) return;\n',
     'public static void onClientTick(ClientTickEvent.Post event) {\n'),
])

sub('mixin/compat/spectate/lrt/MixinLrtClientEventsHandler.java', [
    ('import net.neoforged.neoforge.event.TickEvent;',
     'import net.neoforged.neoforge.client.event.ClientTickEvent;'),
    ('@Inject(method = "tickAnimation(Lnet/minecraftforge/event/TickEvent$ClientTickEvent;)V", at = @At("HEAD"), cancellable = true)\n'
     '    private static void fpsmatch$skipWhenSpectating(TickEvent.ClientTickEvent event, CallbackInfo ci) {',
     '@Inject(method = "tickAnimation(Lnet/neoforged/neoforge/client/event/ClientTickEvent$Post;)V", at = @At("HEAD"), cancellable = true)\n'
     '    private static void fpsmatch$skipWhenSpectating(ClientTickEvent.Post event, CallbackInfo ci) {'),
])

sub('mixin/compat/spectate/tacz/MixinTaczTickAnimationEvent.java', [
    ('import net.neoforged.neoforge.event.TickEvent;',
     'import net.neoforged.neoforge.client.event.ClientTickEvent;\nimport net.neoforged.neoforge.client.event.RenderFrameEvent;'),
    ('@Inject(method = "tickAnimation(Lnet/minecraftforge/event/TickEvent$ClientTickEvent;)V", at = @At("HEAD"), cancellable = true)\n'
     '    private static void fpsmatch$skipWhenSpectating(TickEvent.ClientTickEvent event, CallbackInfo ci) {\n'
     '        if (SpectatorView.isSpectatingOther(Minecraft.getInstance().player)) {\n'
     '            ci.cancel();\n'
     '        }\n'
     '    }',
     '// TaCZ 1.21.1 把原来单一的 tickAnimation(ClientTickEvent) 拆成两个重载，两条路径都要拦。\n'
     '    @Inject(method = "tickAnimation(Lnet/neoforged/neoforge/client/event/ClientTickEvent$Pre;)V", at = @At("HEAD"), cancellable = true)\n'
     '    private static void fpsmatch$skipWhenSpectatingTick(ClientTickEvent.Pre event, CallbackInfo ci) {\n'
     '        if (SpectatorView.isSpectatingOther(Minecraft.getInstance().player)) {\n'
     '            ci.cancel();\n'
     '        }\n'
     '    }\n'
     '\n'
     '    @Inject(method = "tickAnimation(Lnet/neoforged/neoforge/client/event/RenderFrameEvent$Post;)V", at = @At("HEAD"), cancellable = true)\n'
     '    private static void fpsmatch$skipWhenSpectatingFrame(RenderFrameEvent.Post event, CallbackInfo ci) {\n'
     '        if (SpectatorView.isSpectatingOther(Minecraft.getInstance().player)) {\n'
     '            ci.cancel();\n'
     '        }\n'
     '    }'),
])

n = 0
for p in ROOT.rglob('*.java'):
    s = p.read_text(encoding='utf-8')
    if 'EventBusSubscriber.Bus.FORGE' in s:
        p.write_text(s.replace('EventBusSubscriber.Bus.FORGE', 'EventBusSubscriber.Bus.GAME'), encoding='utf-8')
        n += 1
print('  ok  Bus.FORGE -> Bus.GAME：%d 个文件' % n)

sub('compat/impl/FPSMImpl.java', [
    ('import net.neoforged.neoforge.forgespi.language.IModFileInfo;',
     'import net.neoforged.neoforgespi.language.IModFileInfo;'),
])

print()
if miss:
    print('!!! 未命中条目 %d 条：' % len(miss))
    for r, a in miss:
        print('   %s :: %s' % (r, a))
else:
    print('全部替换命中。')
