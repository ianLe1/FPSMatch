#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.21.1 移植定点修复 C：把 compile-9 里成组的 API 差异一次性改掉。
每一条都只做语义等价的机械替换；涉及判断的（KubeJS / 渲染 / Provider）留给后续批次。
"""
import os
import re

ROOT = 'src/main/java'
PKG = 'net/ptcrys/fpsmatch'


def java_files():
    for base, _, names in os.walk(ROOT):
        for n in names:
            if n.endswith('.java'):
                yield os.path.join(base, n)


def add_import(text, imp):
    line = 'import %s;' % imp
    if line in text:
        return text
    lines = text.split('\n')
    idxs = [i for i, l in enumerate(lines) if l.startswith('import ')]
    if idxs:
        for i in idxs:
            if lines[i] > line:
                lines.insert(i, line)
                return '\n'.join(lines)
        lines.insert(idxs[-1] + 1, line)
        return '\n'.join(lines)
    for i, l in enumerate(lines):
        if l.startswith('package '):
            lines.insert(i + 1, '')
            lines.insert(i + 2, line)
            return '\n'.join(lines)
    return text


def recv_start(s, dot):
    i = dot - 1
    while i >= 0 and s[i].isspace():
        i -= 1
    while i >= 0:
        c = s[i]
        if c == ')':
            depth = 0
            while i >= 0:
                if s[i] == ')':
                    depth += 1
                elif s[i] == '(':
                    depth -= 1
                    if depth == 0:
                        break
                i -= 1
            i -= 1
            continue
        if c.isalnum() or c in '_.$':
            i -= 1
            continue
        break
    return i + 1


def close_paren(s, open_idx):
    depth = 0
    k = open_idx
    while k < len(s):
        if s[k] == '(':
            depth += 1
        elif s[k] == ')':
            depth -= 1
            if depth == 0:
                return k
        k += 1
    return -1


def sub_lines(text, fn, guard_comment=True):
    out = []
    for ln in text.split('\n'):
        st = ln.lstrip()
        if guard_comment and (st.startswith('*') or st.startswith('//') or st.startswith('/*')):
            out.append(ln)
            continue
        out.append(fn(ln))
    return '\n'.join(out)


def rewrite_component(s):
    """RECV.writeComponent(X) -> ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(RECV, X)"""
    n = 0
    out = s
    while True:
        m = re.search(r'\.writeComponent\(', out)
        if not m:
            break
        dot = m.start()
        st = recv_start(out, dot)
        recv = out[st:dot]
        cp = close_paren(out, m.end() - 1)
        arg = out[m.end():cp]
        if not recv or recv in ('this', 'super') or 'ComponentSerialization' in recv:
            out = out[:dot] + '\x00' + out[dot + 1:]
            continue
        out = out[:st] + 'ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(%s, %s)' % (recv, arg) + out[cp + 1:]
        n += 1
    m = re.search(r'\.readComponent\(\)', out)
    while m:
        dot = m.start()
        st = recv_start(out, dot)
        recv = out[st:dot]
        if not recv or recv in ('this', 'super') or 'ComponentSerialization' in recv:
            out = out[:dot] + '\x00' + out[dot + 1:]
            m = re.search(r'\.readComponent\(\)', out)
            continue
        out = out[:st] + 'ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(%s)' % recv + out[m.end():]
        n += 1
        m = re.search(r'\.readComponent\(\)', out)
    return out.replace('\x00', '.'), n


def main():
    report = []

    # ---------- 1) 逐文件替换 ----------
    for f in sorted(java_files()):
        text = open(f, encoding='utf-8').read()
        orig = text
        cnt = {}

        # 1.1 writeComponent / readComponent
        text, c = rewrite_component(text)
        if c:
            cnt['component'] = c
            text = add_import(text, 'net.minecraft.network.chat.ComponentSerialization')

        # 1.2 MobEffect -> Holder<MobEffect>
        text, c = re.subn(r'FPSMEffectRegister\.FLASH_BLINDNESS\.get\(\)', 'FPSMEffectRegister.FLASH_BLINDNESS', text)
        if c:
            cnt['holder'] = c

        # 1.3 DefaultedRegistry.getValue -> get
        text, c = re.subn(r'BuiltInRegistries\.ITEM\.getValue\(', 'BuiltInRegistries.ITEM.get(', text)
        if c:
            cnt['itemget'] = c

        # 1.4 ResourceLocation.isValidResourceLocation -> tryParse != null
        text, c = re.subn(r'ResourceLocation\.isValidResourceLocation\(([^()]*)\)',
                          r'(ResourceLocation.tryParse(\1) != null)', text)
        if c:
            cnt['resloc'] = c

        # 1.5 setSecondsOnFire -> igniteForSeconds
        text, c = re.subn(r'\.setSecondsOnFire\(', '.igniteForSeconds(', text)
        if c:
            cnt['ignite'] = c

        # 1.6 defineSynchedData(Builder)
        if 'void defineSynchedData()' in text:
            text, c = re.subn(r'(protected\s+)void defineSynchedData\(\)', r'\1void defineSynchedData(SynchedEntityData.Builder builder)', text)
            text, c2 = re.subn(r'\b(this\.)?entityData\.define\(', 'builder.define(', text)
            cnt['synched'] = c + c2

        # 1.7 FPSMEventHook 的两处误改 + PlayerEvent#getEntity
        if f.endswith('common/event/FPSMEventHook.java'):
            text, c = re.subn(r'event\.setCanPickup\(TriState\.FALSE\)', 'event.setCanceled(true)', text)
            c2 = 0
            for ev in ('PlayerLoggedInEvent', 'PlayerLoggedOutEvent', 'PlayerRespawnEvent'):
                text, k = re.subn(r'(PlayerEvent\.' + ev + r' event\) \{\n\s*if \()event\.getPlayer\(\)',
                                  r'\1event.getEntity()', text)
                c2 += k
            cnt['eventhook'] = c + c2

        # 1.8 post(...) 缺 .isCanceled()（二元 || 形式）
        if 'MixinKeyMappingBlockSpectatorOutlines.java' in f:
            text, c = re.subn(r'(new RequestSpectatorOutlinesEvent\([^()]*\))\)\)',
                              r'\1).isCanceled())', text)
            if c:
                cnt['mixinpost'] = c

        # 1.9 FPSMTeamEvent 可取消
        if f.endswith('common/event/FPSMTeamEvent.java'):
            text = text.replace('public static class JoinEvent extends FPSMTeamEvent {',
                                'public static class JoinEvent extends FPSMTeamEvent implements ICancellableEvent {')
            text = text.replace('public static class LeaveEvent extends FPSMTeamEvent {',
                                'public static class LeaveEvent extends FPSMTeamEvent implements ICancellableEvent {')
            if 'ICancellableEvent' in text:
                text = add_import(text, 'net.neoforged.bus.api.ICancellableEvent')
                cnt['teamcancel'] = 2

        # 1.10 DisplaySlot
        if f.endswith('common/client/screen/TabScreen.java'):
            text, c = re.subn(r'getDisplayObjective\(0\)', 'getDisplayObjective(DisplaySlot.LIST)', text)
            if c:
                text = add_import(text, 'net.minecraft.world.scores.DisplaySlot')
                cnt['displayslot'] = c

        # 1.11 DeltaTracker -> float（仅 CameraEvents，其它事件的 getPartialTick 仍可能是 float）
        if f.endswith('common/client/camera/CameraEvents.java'):
            text, c = re.subn(r'event\.getPartialTick\(\)(?!\s*\.)',
                              'event.getPartialTick().getGameTimeDeltaPartialTick(false)', text)
            if c:
                cnt['deltatracker'] = c

        # 1.12 enqueueWork
        if f.endswith('FPSMatch.java') and 'event.enqueueWork(' in text:
            text, c = re.subn(r'event\.enqueueWork\(\(\) -> \{\n(.*?)\n\s*\}\);',
                              lambda m: '\n'.join(l[4:] if l.startswith('    ') else l for l in m.group(1).split('\n')),
                              text, flags=re.S)
            if c:
                cnt['enqueue'] = c

        # 1.13 PacketDistributor 缺 import
        if 'PacketDistributor.' in text:
            text = add_import(text, 'net.neoforged.neoforge.network.PacketDistributor')

        # 1.14 FPSM_TAB 泛型
        if f.endswith('common/item/FPSMItemRegister.java'):
            text, c = re.subn(r'DeferredHolder<Item, CreativeModeTab> FPSM_TAB',
                              'DeferredHolder<CreativeModeTab, CreativeModeTab> FPSM_TAB', text)
            if c:
                cnt['tab'] = c

        # 1.15 集合编解码用 lambda
        text, c = re.subn(r'FriendlyByteBuf::writeUUID', '(buffer, uuid) -> buffer.writeUUID(uuid)', text)
        text, c2 = re.subn(r'FriendlyByteBuf::readUUID', 'buffer -> buffer.readUUID()', text)
        if c or c2:
            cnt['collambda'] = c + c2

        # 1.16 ItemStack 编解码
        text, c = re.subn(r'(\w+)\.writeItemStack\(([^,]+), false\)',
                          r'ItemStack.STREAM_CODEC.encode((RegistryFriendlyByteBuf) \1, \2)', text)
        text, c2 = re.subn(r'(\w+)\.readItem\(\)',
                           r'ItemStack.STREAM_CODEC.decode((RegistryFriendlyByteBuf) \1)', text)
        if c or c2:
            text = add_import(text, 'net.minecraft.network.RegistryFriendlyByteBuf')
            cnt['itemcodec'] = c + c2

        if text != orig:
            open(f, 'w', encoding='utf-8').write(text)
            report.append((f, cnt))

    for f, cnt in report:
        print('%-95s %s' % (f.replace(ROOT + '/' + PKG + '/', ''), cnt))
    print('共修改 %d 个文件' % len(report))


if __name__ == '__main__':
    os.chdir(os.path.dirname(os.path.abspath(__file__)) + '/..')
    main()
