package net.ptcrys.fpsmatch.compat.warborn;

import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.event.FPSMGunDamageEvent;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * WarBorn Renewed（mod id: {@code warbornrenewed}）弹道防护兼容 —— 软依赖入口（外层门控类）。
 * <p>
 * 背景：WarBorn 的每件护甲通过 {@code ArmorAttributeSpec} 给穿戴者加
 * {@code ModAttributes.BULLET_RESISTANCE}（0~1 减伤比例）与
 * {@code ModAttributes.PROTECTION_CLASS}（0~6 防护等级）两个属性修饰符，
 * 但该 mod 内<b>没有任何代码读取它们</b>（{@code ModAttributes} 的几个 public static
 * 计算方法零调用者，{@code compat.TACZIntegration} 也是死代码）。
 * 也就是说：原版 Warborn 护甲对 TaCZ 枪械目前完全不提供防护。本兼容层就是那个「消费者」。
 * </p>
 * <p>
 * <b>软依赖隔离设计：</b>
 * 本类（外层）只做 {@code ModList.get().isLoaded("warbornrenewed")} 门控，
 * <b>不引用任何 {@code ru.liko.warbornrenewed.*} 类型</b>，
 * 因此即使没装 Warborn，NeoForge 的 {@code AutomaticEventSubscriber} 扫描并加载本类也不会
 * 触发 {@code NoClassDefFoundError}。所有 Warborn 类型引用集中在包内私有类
 * {@link WarbornBallisticHandler}，它只在门控通过、且真的发生一次枪械命中时才会被 JVM 加载。
 * </p>
 * <p>
 * 监听优先级设为 {@link EventPriority#LOW}，保证在
 * {@code GunDamageHandler}（默认 NORMAL，处理 FPSMatch 自带的 BulletproofArmorAttribute）
 * 之后执行，从而不破坏既有防弹衣逻辑、只在其结果上叠加 Warborn 防护。
 * </p>
 */
@EventBusSubscriber(modid = FPSMatch.MODID)
public final class WarbornCompat {

    /** WarBorn Renewed 的 mod id。 */
    public static final String MOD_ID = "warbornrenewed";

    /** 门控结果缓存（null = 尚未查询）。 */
    private static Boolean loadedCache;

    private WarbornCompat() {
    }

    /**
     * WarBorn Renewed 是否已加载。
     * <p>
     * 用 try/catch 兜底：即便在极早期阶段 {@code ModList.get()} 不可用，也只当作「未加载」，
     * 绝不让兼容层把整个服务端拖崩。
     * </p>
     */
    public static boolean isLoaded() {
        Boolean cached = loadedCache;
        if (cached == null) {
            boolean loaded;
            try {
                loaded = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
            } catch (Throwable t) {
                loaded = false;
            }
            loadedCache = loaded;
            cached = loaded;
        }
        return cached;
    }

    /**
     * FPSMatch 枪械伤害事件监听（由 TACZ 桥接层 {@code TACZGunEventBridge} 投递）。
     * <p>
     * 这里只做布尔判断，然后转发给持有 Warborn 类型引用的内层类；
     * Warborn 未加载时该内层类永远不会被解析/加载。
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onGunDamage(FPSMGunDamageEvent event) {
        if (!isLoaded()) {
            return;
        }
        WarbornBallisticHandler.handle(event);
    }
}
