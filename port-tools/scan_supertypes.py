#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NeoForge 1.21.1 规则 3 扫描：被注册成监听器的类的**父类**不允许带 @SubscribeEvent。
错误原文：
  Attempting to register a listener object of type <Sub>,
  however its supertype <Super> has a @SubscribeEvent method: <...>
  This is not allowed! Only the listener object can have @SubscribeEvent methods.
本脚本只在本仓库源码内沿 extends 链查（父类不在此仓库时无法判断）。
"""
import os, re, collections

ROOT = 'src/main/java'


def strip(text):
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    text = re.sub(r'//[^\n]*', '', text)
    return text


# 收集：全限定名 -> (简单名, 父类简单名, @SubscribeEvent 数, 是否 @EventBusSubscriber)
info = {}
simple2fq = {}
for base, _, names in os.walk(ROOT):
    for n in names:
        if not n.endswith('.java'):
            continue
        f = os.path.join(base, n)
        raw = strip(open(f, encoding='utf-8').read())
        pkg = re.search(r'^package\s+([\w.]+);', raw, re.M)
        if not pkg:
            continue
        pkg = pkg.group(1)
        # 主类型（文件里可能还有嵌套类，这里只看顶层）
        m = re.search(r'\n(?:@[\w.]+(?:\([^)]*\))?\s*\n)*\s*(?:public\s+|final\s+|abstract\s+|sealed\s+)*(?:class|interface|enum|record)\s+(\w+)', raw)
        if not m:
            continue
        cls = m.group(1)
        fq = pkg + '.' + cls
        body = raw
        ext = re.search(r'\bclass\s+' + re.escape(cls) + r'\s+extends\s+([\w.]+)', body)
        info[fq] = (cls, ext.group(1) if ext else None,
                    body.count('@SubscribeEvent'),
                    '@EventBusSubscriber' in body)
        simple2fq[cls] = fq

by_simple = {}
for fq, (cls, ext, cnt, ann) in info.items():
    by_simple.setdefault(cls, fq)

print('=== 规则 3：注册类的父类带 @SubscribeEvent ===')
bad = 0
for fq, (cls, ext, cnt, ann) in sorted(info.items()):
    if not ann and not cnt:
        continue
    cur = ext
    chain = []
    seen = set()
    while cur and cur not in seen:
        seen.add(cur)
        pfq = by_simple.get(cur)
        if not pfq:
            chain.append(cur + '(外部)')
            break
        pcls, pext, pcnt, pann = info[pfq]
        chain.append('%s[sub=%d]' % (pcls, pcnt))
        if pcnt:
            print('!! %-70s 父类链 %s  ← 有 @SubscribeEvent' % (fq, ' -> '.join(chain)))
            bad += 1
            break
        cur = pext
print('共 %d 处' % bad)

print()
print('=== 规则 2：@EventBusSubscriber 但自身无 @SubscribeEvent ===')
b2 = [fq for fq, (c, e, n, a) in sorted(info.items()) if a and n == 0]
for fq in b2:
    print('!!', fq)
print('共 %d 处' % len(b2))
