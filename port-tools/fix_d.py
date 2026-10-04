#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.21.1 移植定点修复 D：compile-10 剩余的 53 条唯一错误。
每条都标注了目标 API 的 javap 实证来源（见 commit/日志注释）。
"""
import os
import re

ROOT = 'src/main/java/net/ptcrys/fpsmatch'


def p(rel):
    return os.path.join(ROOT, rel)


def rd(rel):
    return open(p(rel), encoding='utf-8').read()


def wr(rel, text):
    open(p(rel), 'w', encoding='utf-8').write(text)


def sub1(rel, old, new, must=True, count=0):
    t = rd(rel)
    if old not in t:
        if must:
            raise SystemExit('MISS %s :: %r' % (rel, old[:80]))
        return False
    t = t.replace(old, new) if count == 0 else t.replace(old, new, count)
    wr(rel, t)
    return True


def add_import(rel, imp):
    t = rd(rel)
    line = 'import %s;' % imp
    if line in t:
        return
    lines = t.split('\n')
    idxs = [i for i, l in enumerate(lines) if l.startswith('import ')]
    for i in idxs:
        if lines[i] > line:
            lines.insert(i, line)
            break
    else:
        lines.insert(idxs[-1] + 1, line)
    wr(rel, '\n'.join(lines))


# ───────────────────────── 1. 事件取消语义 ─────────────────────────
# ItemEntityPickupEvent.Pre 没有 setCanceled，只有 setCanPickup(TriState)
# （fix_c 的批量替换误伤；ItemTossEvent / ServerChatEvent 那两处是正确写法，必须保留）
sub1('common/event/FPSMEventHook.java',
     """                if (NeoForge.EVENT_BUS.post(pickupItemEvent).isCanceled()) {
                    event.setCanceled(true);
                }""",
     """                if (NeoForge.EVENT_BUS.post(pickupItemEvent).isCanceled()) {
                    // 1.21.1: ItemEntityPickupEvent.Pre 不可取消，改用 setCanPickup(TriState.FALSE)
                    event.setCanPickup(TriState.FALSE);
                }""")
# PlayerEvent 家族只有 getEntity()（PlayerRespawnEvent:142 上一轮漏改）
sub1('common/event/FPSMEventHook.java',
     'if (!(event.getPlayer() instanceof ServerPlayer player)) {',
     'if (!(event.getEntity() instanceof ServerPlayer player)) {')

# ───────────────────────── 2. ClipContext 构造器二义 ─────────────────────────
# 1.21.1 有两个构造器 (…, Entity) 与 (…, CollisionContext)，传裸 null 即二义。
# 保留上游语义（原来就是传 null entity），显式转型消歧。
sub1('common/entity/throwable/FlashBombEntity.java',
     """                ClipContext.Fluid.NONE,
                null);""",
     """                ClipContext.Fluid.NONE,
                (Entity) null);""")

# ───────────────────────── 3. Entity 覆写可见性 ─────────────────────────
# 1.21.1 的 Entity#getBlockPosBelowThatAffectsMyMovement 是 public
sub1('common/entity/MatchDropEntity.java',
     'protected @NotNull BlockPos getBlockPosBelowThatAffectsMyMovement() {',
     'public @NotNull BlockPos getBlockPosBelowThatAffectsMyMovement() {')

# ───────────────────────── 4. Screen 签名（renderBackground / mouseScrolled）─────────────────────────
# 1.21.1: renderBackground(GuiGraphics,int,int,float)；mouseScrolled(double,double,double,double)
sub1('common/client/screen/modernui/ModernScreen.java',
     'public void renderBackground(GuiGraphics graphics) {',
     'public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {')
sub1('common/client/screen/modernui/ModernScreen.java',
     'public boolean mouseScrolled(double x, double y, double delta) {',
     'public boolean mouseScrolled(double x, double y, double scrollX, double delta) {')
sub1('common/client/screen/MatchConfigToolScreen.java',
     'renderBackground(graphics);',
     'renderBackground(graphics, mouseX, mouseY, partialTick);')
sub1('common/client/screen/MatchConfigToolScreen.java',
     'public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {',
     'public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {')

# ───────────────────────── 5. EditBox 没有 tick() ─────────────────────────
# 1.21.1 的 EditBox（及 AbstractWidget）都没有 tick()，删掉这些空转调用
sub1('common/client/screen/MapCreatorToolScreen.java',
     """        super.tick();
        if (this.mapNameField != null) {
            this.mapNameField.tick();
        }
        for (EditBox field : getPosFields()) {
            field.tick();
        }
    }""",
     """        super.tick();
        // 1.21.1: EditBox 不再有 tick()，文本框自身无需每帧轮询
    }""")
sub1('common/client/screen/MatchConfigToolScreen.java',
     """        super.tick();
        valueFields.stream().filter(Objects::nonNull).forEach(EditBox::tick);
    }""",
     """        super.tick();
        // 1.21.1: EditBox 不再有 tick()
    }""")
# Player#getBlockReach() -> blockInteractionRange()
sub1('common/client/screen/MapCreatorToolScreen.java',
     'double reach = minecraft.player.getBlockReach();',
     'double reach = minecraft.player.blockInteractionRange();')

# ───────────────────────── 6. appendHoverText 第 2 参 ─────────────────────────
# 1.21.1: appendHoverText(ItemStack, Item.TooltipContext, List<Component>, TooltipFlag)
HOVER = [
    'common/item/MapCreatorTool.java',
    'common/item/SpawnPointTool.java',
    'common/item/tool/EditToolItem.java',
    'common/item/MatchConfigTool.java',
    'common/item/ShopConfigTool.java',
]
for rel in HOVER:
    t = rd(rel)
    out = []
    for ln in t.split('\n'):
        if 'appendHoverText' in ln:
            ln = ln.replace('@Nullable Level pLevel', 'Item.TooltipContext pContext')
            ln = ln.replace('@Nullable Level level', 'Item.TooltipContext context')
        out.append(ln)
    t = '\n'.join(out)
    t = t.replace('super.appendHoverText(pStack, pLevel,', 'super.appendHoverText(pStack, pContext,')
    wr(rel, t)
    add_import(rel, 'net.minecraft.world.item.Item')

# ───────────────────────── 7. ItemStack <-> NBT（需 HolderLookup.Provider）─────────────────────────
# 运行时字节缓冲始终是 RegistryFriendlyByteBuf（ReflectivePayload 保证），从它取 registryAccess()
ITEMNBT_ADD = '''
    /** 1.21.1：{@code ItemStack#save} 需要 {@link HolderLookup.Provider}。 */
    public static CompoundTag save(HolderLookup.Provider registries, ItemStack stack) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        Tag tag = stack.save(registries);
        return tag instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    /** 1.21.1：替代已删除的 {@code ItemStack.of(CompoundTag)}。 */
    public static ItemStack parse(HolderLookup.Provider registries, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parseOptional(registries, tag);
    }

    /** 1.21.1：替代已删除的 {@code new ItemStack(Item, int, CompoundTag)}。 */
    public static ItemStack of(Item item, int count, CompoundTag tag) {
        ItemStack stack = new ItemStack(item, count);
        if (tag != null && !tag.isEmpty()) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stack;
    }
}
'''
t = rd('util/ItemNbt.java')
assert t.rstrip().endswith('}')
t = t.rstrip()
t = t[:t.rindex('}')].rstrip('\n') + '\n' + ITEMNBT_ADD
t = t.replace('import net.minecraft.core.component.DataComponents;',
              'import net.minecraft.core.HolderLookup;\nimport net.minecraft.core.component.DataComponents;')
t = t.replace('import net.minecraft.nbt.CompoundTag;',
              'import net.minecraft.nbt.CompoundTag;\nimport net.minecraft.nbt.Tag;')
t = t.replace('import net.minecraft.world.item.ItemStack;',
              'import net.minecraft.world.item.Item;\nimport net.minecraft.world.item.ItemStack;')
wr('util/ItemNbt.java', t)

# 7.1 ShopEditorService：加 registries(buf) 帮手 + hash 缓冲改成 RegistryFriendlyByteBuf
t = rd('common/shop/editor/ShopEditorService.java')
t = t.replace('''    static String hash(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());''',
              '''    /**
     * 取序列化用的注册表查询器。包内所有 {@code write/read} 走到的缓冲在线上都是
     * {@link RegistryFriendlyByteBuf}（{@code ReflectivePayload} 保证），哈希路径则由
     * {@link #hash} 显式构造同类型缓冲。
     */
    static HolderLookup.Provider registries(FriendlyByteBuf buf) {
        if (buf instanceof RegistryFriendlyByteBuf registryFriendly) {
            return registryFriendly.registryAccess();
        }
        return FPSMUtil.registryAccess();
    }

    static String hash(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), FPSMUtil.registryAccess());''')
t = t.replace('writeTag(buf, slot.process().save(new CompoundTag()));',
              'writeTag(buf, ItemNbt.save(registries(buf), slot.process()));')
t = t.replace('import net.minecraft.nbt.CompoundTag;',
              'import net.minecraft.core.HolderLookup;\nimport net.minecraft.nbt.CompoundTag;')
t = t.replace('import net.minecraft.network.FriendlyByteBuf;',
              'import net.minecraft.network.FriendlyByteBuf;\nimport net.minecraft.network.RegistryFriendlyByteBuf;')
t = t.replace('import net.ptcrys.fpsmatch.util.FPSMUtil;',
              'import net.ptcrys.fpsmatch.util.FPSMUtil;\nimport net.ptcrys.fpsmatch.util.ItemNbt;')
wr('common/shop/editor/ShopEditorService.java', t)

# 7.2 ListenerModuleSnapshot
t = rd('common/shop/editor/ListenerModuleSnapshot.java')
t = t.replace('''        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(name, 256);
            buf.writeNbt(defaultItem.save(new CompoundTag()));
            buf.writeInt(defaultCost);
            buf.writeNbt(changedItem.save(new CompoundTag()));''',
              '''        public void write(FriendlyByteBuf buf) {
            HolderLookup.Provider registries = ShopEditorService.registries(buf);
            buf.writeUtf(name, 256);
            buf.writeNbt(ItemNbt.save(registries, defaultItem));
            buf.writeInt(defaultCost);
            buf.writeNbt(ItemNbt.save(registries, changedItem));''')
t = t.replace('''        public static Definition read(FriendlyByteBuf buf) {
            String name = buf.readUtf(256);''',
              '''        public static Definition read(FriendlyByteBuf buf) {
            HolderLookup.Provider registries = ShopEditorService.registries(buf);
            String name = buf.readUtf(256);''')
t = t.replace('original == null ? ItemStack.EMPTY : ItemStack.of(original)',
              'ItemNbt.parse(registries, original)')
t = t.replace('changed == null ? ItemStack.EMPTY : ItemStack.of(changed)',
              'ItemNbt.parse(registries, changed)')
t = t.replace('import net.minecraft.nbt.CompoundTag;',
              'import net.minecraft.core.HolderLookup;\nimport net.minecraft.nbt.CompoundTag;')
t = t.replace('import net.minecraft.world.item.ItemStack;',
              'import net.minecraft.world.item.ItemStack;\nimport net.ptcrys.fpsmatch.util.ItemNbt;')
wr('common/shop/editor/ListenerModuleSnapshot.java', t)

# 7.3 ShopEditorSnapshot
t = rd('common/shop/editor/ShopEditorSnapshot.java')
t = t.replace('''        public void write(FriendlyByteBuf buf) {
            buf.writeNbt(item.save(new CompoundTag()));''',
              '''        public void write(FriendlyByteBuf buf) {
            buf.writeNbt(ItemNbt.save(ShopEditorService.registries(buf), item));''')
t = t.replace('''        public static Slot read(FriendlyByteBuf buf) {
            CompoundTag tag = buf.readNbt();
            return new Slot(tag == null ? ItemStack.EMPTY : ItemStack.of(tag), buf.readInt(), buf.readInt(),''',
              '''        public static Slot read(FriendlyByteBuf buf) {
            HolderLookup.Provider registries = ShopEditorService.registries(buf);
            CompoundTag tag = buf.readNbt();
            return new Slot(ItemNbt.parse(registries, tag), buf.readInt(), buf.readInt(),''')
t = t.replace('import net.minecraft.nbt.CompoundTag;',
              'import net.minecraft.core.HolderLookup;\nimport net.minecraft.nbt.CompoundTag;')
t = t.replace('import net.minecraft.world.item.ItemStack;',
              'import net.minecraft.world.item.ItemStack;\nimport net.ptcrys.fpsmatch.util.ItemNbt;')
wr('common/shop/editor/ShopEditorSnapshot.java', t)

# 7.4 ListenerModuleService
t = rd('common/shop/editor/ListenerModuleService.java')
t = t.replace('''    public static void writeDefinition(FriendlyByteBuf buf, ListenerModule module) {
        buf.writeUtf(module.getName(), 256);''',
              '''    public static void writeDefinition(FriendlyByteBuf buf, ListenerModule module) {
        HolderLookup.Provider registries = ShopEditorService.registries(buf);
        buf.writeUtf(module.getName(), 256);''')
t = t.replace('ShopEditorService.writeTag(buf, change.defaultItem().save(new CompoundTag()));',
              'ShopEditorService.writeTag(buf, ItemNbt.save(registries, change.defaultItem()));')
t = t.replace('ShopEditorService.writeTag(buf, change.changedItem().save(new CompoundTag()));',
              'ShopEditorService.writeTag(buf, ItemNbt.save(registries, change.changedItem()));')
# import 注入
t = t.replace('import net.minecraft.network.FriendlyByteBuf;',
              'import net.minecraft.core.HolderLookup;\nimport net.minecraft.network.FriendlyByteBuf;\nimport net.ptcrys.fpsmatch.util.ItemNbt;')
wr('common/shop/editor/ListenerModuleService.java', t)

# ───────────────────────── 8. FPSMUtil.registryAccess() ─────────────────────────
t = rd('util/FPSMUtil.java')
t = t.replace('''    public static ResourceLocation fetchSkin(UUID id, String name) {
        return Minecraft.getInstance().getSkinManager()
                .getInsecureSkinLocation(new GameProfile(id, name));
    }''',
              '''    /**
     * 全局注册表查询器：优先当前服务端，否则退回内置注册表（客户端侧数据包路径不会走到这里，
     * 因为那条路径的缓冲本身就是 {@link net.minecraft.network.RegistryFriendlyByteBuf}）。
     */
    public static RegistryAccess registryAccess() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.registryAccess();
        }
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    public static ResourceLocation fetchSkin(UUID id, String name) {
        return Minecraft.getInstance().getSkinManager()
                .getInsecureSkin(new GameProfile(id, name))
                .texture();
    }''')
t = t.replace('import net.minecraft.core.NonNullList;',
              'import net.minecraft.core.BuiltInRegistries;\nimport net.minecraft.core.NonNullList;\nimport net.minecraft.core.RegistryAccess;')
t = t.replace('import net.minecraft.server.level.ServerPlayer;',
              'import net.minecraft.server.MinecraftServer;\nimport net.minecraft.server.level.ServerPlayer;')
wr('util/FPSMUtil.java', t)

# ───────────────────────── 9. RenderUtil：手工四边形改 1.21.1 缓冲 API ─────────────────────────
t = rd('util/RenderUtil.java')
t = t.replace('''        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        // 构建顶点
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(matrix, x, y, 0).uv(minU, minV).endVertex();
        buffer.vertex(matrix, x, y + height, 0).uv(minU, maxV).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0).uv(maxU, maxV).endVertex();
        buffer.vertex(matrix, x + width, y, 0).uv(maxU, minV).endVertex();

        BufferUploader.drawWithShader(buffer.end());''',
              '''        Matrix4f matrix = poseStack.last().pose();

        // 1.21.1: Tesselator 直接产出 BufferBuilder（getBuilder() 已删），
        // 顶点累积完毕后 buildOrThrow() 得到 MeshData 再交给 BufferUploader。
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, x, y, 0).setUv(minU, minV);
        buffer.addVertex(matrix, x, y + height, 0).setUv(minU, maxV);
        buffer.addVertex(matrix, x + width, y + height, 0).setUv(maxU, maxV);
        buffer.addVertex(matrix, x + width, y, 0).setUv(maxU, minV);

        BufferUploader.drawWithShader(buffer.buildOrThrow());''')
t = t.replace('''        return Minecraft.getInstance().getSkinManager()
                .getInsecureSkinLocation(new GameProfile(id, name));''',
              '''        return Minecraft.getInstance().getSkinManager()
                .getInsecureSkin(new GameProfile(id, name))
                .texture();''')
wr('util/RenderUtil.java', t)

# ───────────────────────── 10. AABB / ItemKey ─────────────────────────
sub1('core/data/AreaData.java',
     'this.aabb = new AABB(pos1, pos2);',
     'this.aabb = AABB.encapsulatingFullBlocks(pos1, pos2);')
sub1('util/ItemKey.java',
     '''        return ItemStack.isSameItemSameTags(
                new ItemStack(item, 1, tag),
                new ItemStack(itemKey.item, 1, itemKey.tag));''',
     '''        return ItemStack.isSameItemSameComponents(
                ItemNbt.of(item, 1, tag),
                ItemNbt.of(itemKey.item, 1, itemKey.tag));''')

# ───────────────────────── 11. KubeJS 兼容层 ─────────────────────────
# 1.21.1 KubeJS(2101.x)：KubeJSPlugin#registerEvents(EventGroupRegistry)；
# KubeEvent#cancel(Context)（带 Rhino Context），不再有 isCancelable()
t = rd('compat/kubejs/FPSMatchKubeJSPlugin.java')
t = t.replace('''    @Override
    public void registerEvents() {
        try {
            FPSMatchCommonEvents.INSTANCE.init();
        } catch (Exception e) {
            // init() 失败不应阻止 FPSMatchEvents 事件组的注册
        }
        FPSMatchKubeJSEvents.GROUP.register();
    }''',
              '''    @Override
    public void registerEvents(EventGroupRegistry registry) {
        try {
            FPSMatchCommonEvents.INSTANCE.init();
        } catch (Exception e) {
            // init() 失败不应阻止 FPSMatchEvents 事件组的注册
        }
        // 1.21.1 KubeJS：事件组交给 EventGroupRegistry 注册（EventGroup#register 已删）
        registry.register(FPSMatchKubeJSEvents.GROUP);
    }''')
t = t.replace('import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;',
              'import dev.latvian.mods.kubejs.event.EventGroupRegistry;\nimport dev.latvian.mods.kubejs.plugin.KubeJSPlugin;')
wr('compat/kubejs/FPSMatchKubeJSPlugin.java', t)

t = rd('compat/kubejs/events/FPSMatchKubeJSEvents.java')
t = t.replace('''        @Override
        public Object cancel() throws EventExit {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return super.cancel();
        }''',
              '''        @Override
        public Object cancel(Context cx) throws EventExit {
            // 1.21.1: NeoForge 用 ICancellableEvent 表达可取消性
            if (event instanceof ICancellableEvent cancellable) {
                cancellable.setCanceled(true);
            }
            return super.cancel(cx);
        }''')
t = t.replace('import net.neoforged.bus.api.Event;',
              'import net.neoforged.bus.api.Event;\nimport net.neoforged.bus.api.ICancellableEvent;')
t = t.replace('import dev.latvian.mods.kubejs.event.KubeEvent;',
              'import dev.latvian.mods.kubejs.event.KubeEvent;\nimport dev.latvian.mods.rhino.Context;')
wr('compat/kubejs/events/FPSMatchKubeJSEvents.java', t)

print('fix_d 完成')
