#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.21.1 移植定点修复 B
  1) 新增 util/ItemNbt 垫片：ItemStack 的 getOrCreateTag/getTag/setTag -> DataComponents.CUSTOM_DATA
  2) 把全仓 ItemStack 的这三处旧调用改到垫片上
  3) 删除所有 @Override isCancelable()（1.21.1 的 Event 已无此方法），
     返回 true 的类补 implements ICancellableEvent
脚本幂等：重复运行不会重复改写。
"""
import os
import re
import sys

ROOT = 'src/main/java'
PKG = 'net/ptcrys/fpsmatch'

ITEMNBT = '''package net.ptcrys.fpsmatch.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 1.21.1 移植垫片：{@code ItemStack} 的 NBT 读写已从 {@code getTag()/getOrCreateTag()/setTag()}
 * 改为 {@code DataComponents.CUSTOM_DATA} 组件。
 *
 * <p>本类刻意保持旧 API 的语义——{@link #getOrCreateTag} 返回的 {@link CompoundTag}
 * <b>就是栈上持有的那个实例</b>（{@code CustomData#getUnsafe()} 不复制），因此调用方对它的
 * 原地修改会自动生效，无需额外回写。这与 1.20.1 的行为一致，也避免逐点改写为
 * {@code CustomData.update(...)} 时漏掉某条写路径。</p>
 */
public final class ItemNbt {

    private ItemNbt() {
    }

    /** 等价于 1.20.1 的 {@code ItemStack#getOrCreateTag()}。 */
    public static CompoundTag getOrCreateTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            return data.getUnsafe();
        }
        CompoundTag tag = new CompoundTag();
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return tag;
    }

    /** 等价于 1.20.1 的 {@code ItemStack#getTag()}，没有则返回 {@code null}。 */
    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.getUnsafe();
    }

    /** 等价于 1.20.1 的 {@code ItemStack#setTag(CompoundTag)}，传 {@code null} 即移除。 */
    public static void setTag(ItemStack stack, CompoundTag tag) {
        if (tag == null) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
}
'''


def java_files():
    for base, _, names in os.walk(ROOT):
        for n in names:
            if n.endswith('.java'):
                yield os.path.join(base, n)


def add_import(text, imp, same_package):
    if same_package:
        return text
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
    """从 `.` 的位置向前找出接收者表达式的起点索引。"""
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


def wrap_noarg(s, meth, prefix):
    """RECV.meth()  ->  prefix(RECV)   ；返回 (新串, 次数)"""
    n = 0
    out = s
    while True:
        m = re.search(r'\.%s\(\)' % re.escape(meth), out)
        if not m:
            break
        dot = m.start()
        st = recv_start(out, dot)
        recv = out[st:dot]
        if not recv or recv in ('this', 'super', 'ItemNbt') or recv.startswith('import ') or recv.startswith('*'):
            # 无法安全改写：打标记后跳过
            print('  !! 跳过 %s.%s()  (recv=%r)' % (meth, meth, recv))
            out = out[:dot] + '\x00' + out[dot + 1:]
            continue
        out = out[:st] + '%s(%s)' % (prefix, recv) + out[m.end():]
        n += 1
    return out.replace('\x00', '.'), n


def wrap_onearg(s, meth, prefix):
    """RECV.meth(A)  ->  prefix(RECV, A)"""
    n = 0
    out = s
    while True:
        m = re.search(r'\.%s\(' % re.escape(meth), out)
        if not m:
            break
        dot = m.start()
        st = recv_start(out, dot)
        recv = out[st:dot]
        if not recv or recv in ('this', 'super', 'ItemNbt'):
            print('  !! 跳过 .%s(  (recv=%r)' % (meth, recv))
            out = out[:dot] + '\x00' + out[dot + 1:]
            continue
        # 配对右括号
        depth = 0
        k = m.end() - 1
        while k < len(out):
            if out[k] == '(':
                depth += 1
            elif out[k] == ')':
                depth -= 1
                if depth == 0:
                    break
            k += 1
        args = out[m.end():k]
        out = out[:st] + '%s(%s, %s)' % (prefix, recv, args) + out[k + 1:]
        n += 1
    return out.replace('\x00', '.'), n


def fix_nbt():
    print('=== 1) ItemNbt 垫片 ===')
    p = os.path.join(ROOT, PKG, 'util', 'ItemNbt.java')
    old = open(p, encoding='utf-8').read() if os.path.exists(p) else None
    if old != ITEMNBT:
        open(p, 'w', encoding='utf-8').write(ITEMNBT)
        print('  写入 %s' % p)
    else:
        print('  已是最新')

    print('=== 2) ItemStack NBT 旧调用点 ===')
    totals = {'getOrCreateTag': 0, 'getTag': 0, 'setTag': 0}
    touched = []
    for f in sorted(java_files()):
        text = open(f, encoding='utf-8').read()
        lines = text.split('\n')
        hit = 0
        out = []
        for ln in lines:
            stripped = ln.lstrip()
            if stripped.startswith('*') or stripped.startswith('//') or stripped.startswith('/*'):
                out.append(ln)
                continue
            new = ln
            for meth in ('getOrCreateTag', 'getTag'):
                new, c = wrap_noarg(new, meth, 'ItemNbt.' + meth)
                if c:
                    totals[meth] += c
                    hit += c
            if 'setTag(' in new and '.setTag(' in new:
                new, c = wrap_onearg(new, 'setTag', 'ItemNbt.setTag')
                if c:
                    totals['setTag'] += c
                    hit += c
            out.append(new)
        if hit:
            newtext = '\n'.join(out)
            newtext = add_import(newtext, 'net.ptcrys.fpsmatch.util.ItemNbt',
                                 os.path.dirname(f).replace('\\', '/') == (ROOT + '/' + PKG + '/util').replace('\\', '/'))
            open(f, 'w', encoding='utf-8').write(newtext)
            touched.append((f, hit))
    for f, h in touched:
        print('  %-90s %d' % (f, h))
    print('  合计: %s' % totals)
    return len(touched)


def strip_is_cancelable():
    print('=== 3) isCancelable() -> ICancellableEvent ===')
    total_rm = 0
    total_impl = 0
    for f in sorted(java_files()):
        text = open(f, encoding='utf-8').read()
        if 'isCancelable' not in text:
            continue
        lines = text.split('\n')
        drop = set()
        ifaces = []          # 需要补 ICancellableEvent 的【类名】
        i = 0
        while i < len(lines) - 3:
            if (lines[i].strip() == '@Override'
                    and 'isCancelable()' in lines[i + 1]
                    and lines[i + 1].strip().endswith('{')
                    and lines[i + 2].strip() in ('return true;', 'return false;')
                    and lines[i + 3].strip() == '}'):
                ret = lines[i + 2].strip() == 'return true;'
                start = i
                j = i - 1
                while j >= 0 and lines[j].strip().startswith('@'):
                    start = j
                    j -= 1
                cls = None
                for k in range(start - 1, -1, -1):
                    if re.match(r'^\s*(?:public\s+|protected\s+|private\s+)?(?:static\s+)?(?:final\s+|abstract\s+)?class\s+\w+', lines[k]):
                        cls = k
                        break
                if ret and cls is not None:
                    ifaces.append(re.search(r'class\s+(\w+)', lines[cls]).group(1))
                else:
                    print('  [false 仅删除] %s:%d' % (f, i + 2))
                drop.update(range(start, i + 4))
                total_rm += 1
                i += 4
            else:
                i += 1
        if not drop:
            continue
        keep = [l for n, l in enumerate(lines) if n not in drop]
        for name in sorted(set(ifaces)):
            for n, cand in enumerate(keep):
                if re.match(r'^\s*(?:public\s+|protected\s+|private\s+)?(?:static\s+)?(?:final\s+|abstract\s+)?class\s+%s\b' % re.escape(name), cand):
                    if 'implements' in cand:
                        if 'ICancellableEvent' not in cand:
                            keep[n] = cand.replace('implements', 'implements ICancellableEvent,', 1)
                            total_impl += 1
                    else:
                        keep[n] = cand.rstrip()
                        if keep[n].endswith('{'):
                            keep[n] = keep[n][:-1].rstrip() + ' implements ICancellableEvent {'
                        else:
                            keep[n] = keep[n] + ' implements ICancellableEvent'
                        total_impl += 1
                    break
        newtext = add_import('\n'.join(keep), 'net.neoforged.bus.api.ICancellableEvent', False)
        open(f, 'w', encoding='utf-8').write(newtext)
        print('  %-90s 删除 %d 处 isCancelable' % (f, sum(1 for n in drop if lines[n].strip() == '@Override')))
    print('  合计: 删除 %d 处，补 implements %d 个类' % (total_rm, total_impl))


if __name__ == '__main__':
    os.chdir(os.path.dirname(os.path.abspath(__file__)) + '/..')
    n = fix_nbt()
    strip_is_cancelable()
    print('完成')
